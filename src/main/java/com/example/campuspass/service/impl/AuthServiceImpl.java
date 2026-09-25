package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.campuspass.dto.*;
import com.example.campuspass.entity.User;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.service.IAuthService;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.JwtUtils;
import com.example.campuspass.utils.LimitRateUtils;
import com.example.campuspass.utils.RegexUtils;
import com.example.campuspass.utils.VerifyCodeUtils;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import com.example.campuspass.vo.LoginVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.campuspass.utils.RedisConstants.LOGIN_TTL;
import static com.example.campuspass.utils.RegexConstants.NICK_NAME_REGEX;

@Service
public class AuthServiceImpl implements IAuthService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private LimitRateUtils limitRateUtils;

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
            error(40000, "手机号有误");
        }
        String key="login:code:" + phone;
        timesCheck("login:times:" + "send:" + phone);
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
            error(40000, "手机号或密码有误");
        }

        timesCheck("login:times:"+"password:" + phone);
        User user = userService.lambdaQuery().eq(User::getPhone, phone).one();
        if(user==null){
            error(40000, "手机号或密码有误");
        }

        String encodedPassword = user.getPassword();
        if (!passwordEncoder.matches(password,encodedPassword)) {
            error(40000, "手机号或密码有误");
        }
        String token = getToken(user);

        return Result.ok(new LoginVO(token, user.getUserId(), user.getUserName(), user.getRole()));

    }

    public static void error(int code, String message) {
        throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }


    @Override
    public Result<Void> logOut(HttpServletRequest request) {

        String header = request.getHeader("Authorization");
        if (StrUtil.isBlank(header) || !header.startsWith("Bearer ")) {
            error(40000, "请求头内容非法");
        }
        String jwtToken = header.substring(7);
        Claims claims = jwtUtils.parseToken(jwtToken);
        String token = "login:status:" + claims.get("sid", String.class);
        Boolean success = stringRedisTemplate.delete(token);
        if(!success){
            error(40000, "缓存删除失败");
        }
        return Result.ok();
    }
    @Override
    public Result<Void> signUp(SignUpDTO signUpDTO) {
        String phone = signUpDTO.getPhone();
        String code = signUpDTO.getCode();
        String password = signUpDTO.getPassword();
        String key="login:code:"+phone;
        String userName = signUpDTO.getUserName();
        timesCheck("login:times:" + "code:" + phone);
        if (StrUtil.isBlank(userName)) {
            signUpDTO.setUserName(UUID.randomUUID().toString());
        }else{
            if (!userName.matches(NICK_NAME_REGEX)) {
                error(40000, "用户名不能包含空白字符、换行符或控制字符");
            }
            int sum = userName.codePoints()
                    .map(cp -> Character.UnicodeScript.of(cp)
                            == Character.UnicodeScript.HAN ? 2 : 1).sum();
            if (sum < 4 || sum > 20) {
                error(40000, "名字过长或过短，中文2~10，英文4~20");
            }
        }

        Long result = stringRedisTemplate.execute(CONSUME_CODE, List.of(key), code);
        if (result==null||result == 0L) {
            error(40000, "手机号或验证码有误");
        }
        User user = BeanUtil.copyProperties(signUpDTO, User.class);

        String encodedPassword = passwordEncoder.encode(password);
        user.setPassword(encodedPassword);

        user.setRole(0);
        user.setStatus(0);
        try {
            userService.save(user);
        } catch (DuplicateKeyException e) {
            error(40000, "用户名,手机号或者学号已经被注册过了");
        }
        return Result.ok();

    }

    @Override
    public Result<LoginVO> loginWithCode(CodeLoginDTO codeLoginDTO) {
        String code=codeLoginDTO.getCode();
        String phone=codeLoginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            error(40000, "手机号或验证码有误");
        }
        String key = "login:code:" + phone;
        timesCheck("login:times:"+"code:" + phone);
        User user = userService.lambdaQuery().eq(User::getPhone, phone).one();
        if(user==null){
            error(40000, "手机号或验证码有误");
        }

        Long result= stringRedisTemplate.execute(CONSUME_CODE, Collections.singletonList(key), code);
        if (result == null || result == 0L) {
            error(40000, "手机号或验证码有误");
        }
        String token = getToken(user);
        return Result.ok(new LoginVO(token, user.getUserId(), user.getUserName(), user.getRole()));

    }

    public  void timesCheck(String timesKey) {
        Long execute = limitRateUtils.execute(timesKey, 5L, 300L);
        if(execute==-1){
            error(50000, "服务器异常");
        }
        if(execute==0){
            error(40000, "输入次数过多");
        }
    }

    public @NonNull String getToken(User user) {

        if(user.getStatus()==1){
            error(40000, "账号已被禁用");
        }
        String sessionId=UUID.randomUUID().toString();
        String redisKey="login:status:" + sessionId;
        stringRedisTemplate.opsForValue().set(redisKey, user.getUserId().toString()
                , Duration.ofMinutes(LOGIN_TTL));
        Map<String, Object> claim = new HashMap<>();
        claim.put("sid",sessionId);
        saveUserCache(user);
        return jwtUtils.generateToken(claim);
    }

    public void saveUserCache(User user) {
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        executeSaveHashMapLua.executeScript(BeanUtil.beanToMap(userDTO), "user:user-id:" + user.getUserId(), 2 * 60L);
    }


}
