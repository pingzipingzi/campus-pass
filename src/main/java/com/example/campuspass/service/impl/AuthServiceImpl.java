package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.campuspass.dto.*;
import com.example.campuspass.entity.User;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.service.IAuthService;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.JwtUtils;
import com.example.campuspass.utils.lua_execute.ExecuteLimitRate;
import com.example.campuspass.utils.RegexUtils;
import com.example.campuspass.utils.VerifyCodeUtils;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import com.example.campuspass.vo.LoginVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

import static com.example.campuspass.utils.RedisConstants.CODE_VERIFY_RATE_LIMIT_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.LOGIN_CODE_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.LOGIN_STATUS_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.PASSWORD_LOGIN_RATE_LIMIT_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.RESET_PASSWORD_RATE_LIMIT_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.SEND_CODE_RATE_LIMIT_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_TTL_SECONDS;
import static com.example.campuspass.utils.RedisConstants.LOGIN_TTL;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class AuthServiceImpl implements IAuthService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ExecuteLimitRate limitRateUtils;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ExecuteSaveHashMapLua executeSaveHashMapLua;

    @Autowired
    private JwtUtils jwtUtils;

    private static final DefaultRedisScript<Long> CONSUME_CODE;

    static {
        CONSUME_CODE = new DefaultRedisScript<>();
        CONSUME_CODE.setLocation(new ClassPathResource("lua/consume_code.lua"));
        CONSUME_CODE.setResultType(Long.class);
    }
    @Autowired
    private IUserService userService;
    @Override
    public Result<Void> sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            error( "手机号有误");
        }
        String key=LOGIN_CODE_KEY_PREFIX + phone;
        timesCheck(SEND_CODE_RATE_LIMIT_KEY_PREFIX + phone);
        if (stringRedisTemplate.hasKey(key)) {
            return Result.ok();
        }
        String code = VerifyCodeUtils.generateCode();

        stringRedisTemplate.opsForValue().setIfAbsent(key, code, Duration.ofMinutes(5));
        return Result.ok();
    }

    @Override
    public Result<LoginVO> loginWithPassword(PasswordLoginDTO passwordLoginDTO) {
        String password = passwordLoginDTO.getPassword();
        String phone = passwordLoginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            error( "手机号或密码有误");
        }

        timesCheck(PASSWORD_LOGIN_RATE_LIMIT_KEY_PREFIX + phone);
        User user = userService.lambdaQuery().eq(User::getPhone, phone).one();
        if(user==null){
            error( "手机号或密码有误");
        }

        String encodedPassword = user.getPassword();
        if (!passwordEncoder.matches(password,encodedPassword)) {
            error( "手机号或密码有误");
        }
        String token = getToken(user);

        return Result.ok(new LoginVO(token, user.getUserId(), user.getUserName(), user.getRole()));

    }

    public static void error( String message) {
        throw new BusinessException(40000, message, HttpStatus.BAD_REQUEST);
    }


    @Override
    public Result<Void> logOut(HttpServletRequest request) {

        String header = request.getHeader("Authorization");
        if (StrUtil.isBlank(header) || !header.startsWith("Bearer ")) {
            error( "请求头内容非法");
        }
        String jwtToken = header.substring(7);
        Claims claims = jwtUtils.parseToken(jwtToken);
        String token = LOGIN_STATUS_KEY_PREFIX + claims.get("sid", String.class);
        Boolean success = stringRedisTemplate.delete(token);
        if(!success){
            error( "缓存删除失败");
        }
        return Result.ok();
    }

    @Override
    public Result<Void> signUp(SignUpDTO signUpDTO) {
        String phone = signUpDTO.getPhone();
        String code = signUpDTO.getCode();
        String password = signUpDTO.getPassword();
        String key=LOGIN_CODE_KEY_PREFIX+phone;
        String userName = signUpDTO.getUserName();
        timesCheck(CODE_VERIFY_RATE_LIMIT_KEY_PREFIX + phone);
        if (StrUtil.isBlank(userName)) {
            signUpDTO.setUserName(UUID.randomUUID().toString());
        }else{
            RegexUtils.validateNickName(userName);
        }

        consumeCode(key, code);
        User user = BeanUtil.copyProperties(signUpDTO, User.class);

        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);

        user.setRole(0);
        user.setStatus(0);
        try {
            userService.save(user);
        } catch (DuplicateKeyException e) {
            error( "用户名,手机号或者学号已经被注册过了");
        }
        return Result.ok();

    }

    @Override
    public Result<Void> changePassword(ChangePasswordDTO changePasswordDTO) {
        Long userId = getUserId();
        User user = userService.lambdaQuery().eq(User::getUserId, userId).one();
        if (user == null) {
            error("用户不存在");
        }
        String oldPassword = changePasswordDTO.getOldPassword();
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            error("原密码输入有误");
        }
        String newPassword = changePasswordDTO.getNewPassword();
        setNewPassword(newPassword, user);
        return Result.ok();
    }

    @Override
    public Result<Void> forgetPassword(ForgetPasswordDTO forgetPasswordDTO) {
        timesCheck(RESET_PASSWORD_RATE_LIMIT_KEY_PREFIX + forgetPasswordDTO.getPhone());
        User user = userService.lambdaQuery().eq(User::getPhone, forgetPasswordDTO.getPhone()).one();
        if (user == null) {
            error("手机号或验证码有误");
        }
        String key = LOGIN_CODE_KEY_PREFIX + forgetPasswordDTO.getPhone();
        consumeCode(key, forgetPasswordDTO.getCode());
        String newPassword = forgetPasswordDTO.getNewPassword();
        setNewPassword(newPassword, user);

        return Result.ok();
    }

    public void setNewPassword(String newPassword1, User user) {
        String newPassword = passwordEncoder.encode(newPassword1);
        boolean update = userService.lambdaUpdate().eq(User::getUserId, user.getUserId()).eq(User::getStatus, 0)
                .eq(User::getPassword, user.getPassword()).eq(User::getAuthVersion, user.getAuthVersion())
                .set(User::getPassword, newPassword).set(User::getAuthVersion, user.getAuthVersion() + 1).update();
        if (!update) {
            error("数据更新失败");
        }
        String keyForUser = USER_CACHE_KEY_PREFIX + user.getUserId();
        stringRedisTemplate.delete(keyForUser);

    }

    public static @NonNull Long getUserId() {
        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if (userDTO == null) {
            error("用户未登录");
        }
        return userDTO.getUserId();
    }


    @Override
    public Result<LoginVO> loginWithCode(CodeLoginDTO codeLoginDTO) {
        String code=codeLoginDTO.getCode();
        String phone=codeLoginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            error( "手机号或验证码有误");
        }
        String key = LOGIN_CODE_KEY_PREFIX + phone;
        timesCheck(CODE_VERIFY_RATE_LIMIT_KEY_PREFIX + phone);
        User user = userService.lambdaQuery().eq(User::getPhone, phone).one();
        if(user==null){
            error( "手机号或验证码有误");
        }

        consumeCode(key, code);
        String token = getToken(user);
        return Result.ok(new LoginVO(token, user.getUserId(), user.getUserName(), user.getRole()));

    }

    public void consumeCode(String key, String code) {
        Long result= stringRedisTemplate.execute(CONSUME_CODE,List.of(key), code);
        if (result == null || result == 0L) {
            error( "手机号或验证码有误");
        }
    }

    public  void timesCheck(String timesKey) {
        Long execute = limitRateUtils.execute(timesKey, 5L, 300L);
        if(execute==-1){
            error( "服务器异常");
        }
        if(execute==0){
            error( "重试次数过多");
        }
    }

    public @NonNull String getToken(User user) {

        if(user.getStatus()==1){
            error( "账号已被禁用");
        }
        String sessionId=user.getAuthVersion()+"&"+UUID.randomUUID().toString();
        String redisKey=LOGIN_STATUS_KEY_PREFIX + sessionId;
        stringRedisTemplate.opsForValue().set(redisKey, user.getUserId().toString()
                , Duration.ofMinutes(LOGIN_TTL));
        Map<String, Object> claim = new HashMap<>();
        claim.put("sid",sessionId);
        saveUserCache(user);
        return jwtUtils.generateToken(claim);
    }

    public void saveUserCache(User user) {
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        executeSaveHashMapLua.executeScript(BeanUtil.beanToMap(userDTO), USER_CACHE_KEY_PREFIX + user.getUserId(), USER_CACHE_TTL_SECONDS);
    }


}
