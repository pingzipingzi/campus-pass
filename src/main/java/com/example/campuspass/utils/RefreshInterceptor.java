package com.example.campuspass.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.jwt.JWTException;
import com.example.campuspass.entity.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

import static com.example.campuspass.utils.RedisConstants.LOGIN_TTL;

@Slf4j
public class RefreshInterceptor implements HandlerInterceptor {
    public static final ThreadLocal<User> USER_THREAD_LOCAL = new ThreadLocal<>();

    private final StringRedisTemplate stringRedisTemplate;
    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        USER_THREAD_LOCAL.remove();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String jwtToken = request.getHeader("Authorization");
        Claims claim = null;
        try {
            claim = JwtUtils.parseToken(jwtToken);
        } catch (JWTException e) {
            log.error("JWT签名不匹配或已过期", e);
            return true;
        }
        String token = claim.get("token",String.class);
        if (StrUtil.isBlank(token)) {
            return true;
        }
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(token);
        if(map.isEmpty()){
            return true;
        }
        User user = BeanUtil.fillBeanWithMap(map, new User(), false);
        USER_THREAD_LOCAL.set(user);
        stringRedisTemplate.expire(token, Duration.ofMinutes(LOGIN_TTL));
        return true;
    }
}
