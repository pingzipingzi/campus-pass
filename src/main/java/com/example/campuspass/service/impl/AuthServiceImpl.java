package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.example.campuspass.dto.CodeLoginDTO;
import com.example.campuspass.dto.PasswordLoginDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.SignUpDTO;
import com.example.campuspass.entity.User;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.service.IAuthService;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.JwtUtils;
import com.example.campuspass.utils.RegexUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import javax.print.attribute.HashAttributeSet;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.example.campuspass.utils.RedisConstants.LOGIN_TTL;

public class AuthServiceImpl implements IAuthService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;
    @Override
    public Result<Void> sendCode(String phone) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new BusinessException(40000, "电话格式有误", HttpStatus.BAD_REQUEST);
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set("login:code:" + phone, code, Duration.ofMinutes(5));
        return Result.ok();
    }

    @Override
    public Result<String> loginWithPassword(PasswordLoginDTO passwordLoginDTO) {
        String password = passwordLoginDTO.getPassword();
        String phone = passwordLoginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new BusinessException(40000, "电话号格式有误", HttpStatus.BAD_REQUEST);
        }
        String cachePassword = stringRedisTemplate.opsForValue().get("login:password:" + phone);
        if (StrUtil.isBlank(cachePassword)) {
            throw new BusinessException(40000, "用户未注册", HttpStatus.BAD_REQUEST);
        }
        if (!password.equals(cachePassword)) {
            throw new BusinessException(40000, "账号密码不匹配", HttpStatus.BAD_REQUEST);
        }
        String token = getToken(phone);
        return Result.ok(token);

    }

    @Override
    public Result<Void> signUp(SignUpDTO signUpDTO) {
        if (StrUtil.isBlank(signUpDTO.getUserName())) {
            signUpDTO.setUserName(UUID.randomUUID().toString());
        }

        User user = BeanUtil.copyProperties(signUpDTO, User.class);
        userService.save(user);
        return Result.ok();

    }

    @Override
    public Result<Void> logOut(HttpServletRequest request) {

        String header = request.getHeader("Authorization");
        if (StrUtil.isBlank(header) || !header.startsWith("bearer")) {
            throw new BusinessException(40000, "请求头内容非法", HttpStatus.BAD_REQUEST);
        }
        String jwtToken = header.substring(7);
        Claims claims = JwtUtils.parseToken(jwtToken);
        String token = claims.get("token", String.class);
        Boolean success = stringRedisTemplate.delete(token);
        if(!success){
            throw new BusinessException(40000, "缓存删除失败", HttpStatus.BAD_REQUEST);
        }
        return Result.ok();
    }

    @Override
    public Result<String> loginWithCode(CodeLoginDTO codeLoginDTO) {
        String code=codeLoginDTO.getCode();
        String phone=codeLoginDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            throw new BusinessException(40000, "电话号格式有误", HttpStatus.BAD_REQUEST);
        }
        String cacheCode = stringRedisTemplate.opsForValue().get("login:code:" + phone);
        if (StrUtil.isBlank(cacheCode)) {
            throw new BusinessException(40000, "用户未注册", HttpStatus.BAD_REQUEST);
        }
        if ( !code.equals(cacheCode)) {
            throw new BusinessException(40000, "验证码有误", HttpStatus.BAD_REQUEST);
        }
        String token = getToken(phone);
        return Result.ok(token);

    }

    public @NonNull String getToken(String phone) {
        User user = userService.lambdaQuery().eq(User::getPhone, phone).one();
        if(user.getStatus()==1){
            throw new BusinessException(40000, "账号已被禁用", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> map = BeanUtil.beanToMap(user, new HashMap<>()
                , CopyOptions.create().ignoreNullValue()
                        .setFieldValueEditor(
                                (fieldName, fieldValue) ->
                                        (fieldValue == null) ? null : fieldValue.toString()
                        ));
        String token="login:status:" + UUID.randomUUID();
        stringRedisTemplate.opsForHash().putAll(token,map);
        stringRedisTemplate.expire(token, Duration.ofMinutes(LOGIN_TTL));
        Map<String, Object> claim = new HashMap<>();
        claim.put("token",token);
        return JwtUtils.generateToken(claim);
    }


}
