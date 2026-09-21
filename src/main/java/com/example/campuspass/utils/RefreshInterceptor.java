package com.example.campuspass.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.example.campuspass.entity.User;
import com.example.campuspass.vo.UserVO;
import io.jsonwebtoken.*;
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

    private final JwtUtils jwtUtils ;


    private final StringRedisTemplate stringRedisTemplate;
    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate,JwtUtils jwtUtils){
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils=jwtUtils;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        USER_THREAD_LOCAL.remove();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String header = request.getHeader("Authorization");
        if (StrUtil.isBlank(header) || !header.startsWith("Bearer ") || header.length() < 8) {
            return true;
        }
        String jwtToken = header.substring(7);
        if (StrUtil.isBlank(jwtToken)) {
            return true;
        }
        Claims claim = null;
        try {
            claim = jwtUtils.parseToken(jwtToken);
        } catch (Exception e) {
            log.error("JWT签名不匹配或已过期", e);
            return true;
        }

        String sessionId=claim.get("sid",String.class);
        if (sessionId == null || !sessionId.matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            return true;
            // 按无效登录凭据处理，不继续访问 Redis
        }
        String token ="login:status:"+sessionId;
        if (StrUtil.isBlank(token)) {
            return true;
        }
        String sessionJson = stringRedisTemplate.opsForValue().get(token);
        if (StrUtil.isBlank(sessionJson)) {
            return true;
        }
        UserVO userVO = JSONUtil.toBean(sessionJson, UserVO.class);
        if (userVO == null
                || userVO.getUserId() == null
                || userVO.getUserId() <= 0) {
            return true;
        }
        Boolean isSuccess = stringRedisTemplate.expire(token, Duration.ofMinutes(LOGIN_TTL));
        if (!BooleanUtil.isTrue(isSuccess)) {
            return true;
        }
        try {
            User user = BeanUtil.copyProperties(userVO, User.class);
            USER_THREAD_LOCAL.set(user);
        } catch (Exception e) {
            log.error(e.toString());
            USER_THREAD_LOCAL.remove();
        }
        return true;
    }


}
