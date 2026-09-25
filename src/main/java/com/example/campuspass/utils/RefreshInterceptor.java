package com.example.campuspass.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.campuspass.entity.User;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import io.jsonwebtoken.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;

import static com.example.campuspass.utils.RedisConstants.LOGIN_STATUS_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.USER_CACHE_TTL_SECONDS;
import static com.example.campuspass.utils.RedisConstants.LOGIN_TTL;

@Slf4j
public class RefreshInterceptor implements HandlerInterceptor {
    public static final ThreadLocal<UserDTO> USER_THREAD_LOCAL = new ThreadLocal<>();

    private final JwtUtils jwtUtils ;
    
    private final IUserService userService;

    private final ExecuteSaveHashMapLua executeSaveHashMapLua;

    private final StringRedisTemplate stringRedisTemplate;
    public RefreshInterceptor(StringRedisTemplate stringRedisTemplate, JwtUtils jwtUtils, IUserService userService, ExecuteSaveHashMapLua executeSaveHashMapLua){
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtUtils=jwtUtils;
        this.userService=userService;
        this.executeSaveHashMapLua = executeSaveHashMapLua;
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
                "^[0-9]+&[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            return true;
            // 按无效登录凭据处理，不继续访问 Redis
        }
        String token =LOGIN_STATUS_KEY_PREFIX+sessionId;
        Long authVersion = null;
        try {
            authVersion = Long.valueOf(sessionId.substring(0, sessionId.indexOf('&')));
        } catch (NumberFormatException e) {
            return true;
        }

        String userId = stringRedisTemplate.opsForValue().get(token);
        if (StrUtil.isBlank(userId)) {
            return true;
        }

        String keyForUser = USER_CACHE_KEY_PREFIX + userId;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(keyForUser);

        UserDTO userDTO = null;

        if (map==null||map.isEmpty()) {
            User user = userService.lambdaQuery()
                    .eq(User::getUserId, userId)
                    .one();
            if (user == null
                    || !Integer.valueOf(0).equals(user.getStatus())
                    ||user.getAuthVersion()==null
                    ||!authVersion.equals(user.getAuthVersion())) {
                stringRedisTemplate.delete(token);
                return true;
            }
            userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            if (executeScriptSaveHash(userDTO, keyForUser)) return true;
        }else {

            userDTO = BeanUtil.fillBeanWithMap(map, new UserDTO(), false);

            if (userDTO.getAuthVersion() == null||
                    !Integer.valueOf(0).equals(userDTO.getStatus())
                    ||!authVersion.equals(userDTO.getAuthVersion())) {
                User user1 = userService.lambdaQuery().eq(User::getUserId, userId).one();
                if (user1==null||
                        !Integer.valueOf(0).equals(user1.getStatus())
                        || !authVersion.equals(user1.getAuthVersion())) {
                    stringRedisTemplate.delete(token);
                    return true;
                }else{
                    userDTO = BeanUtil.copyProperties(user1, UserDTO.class);
                    if (executeScriptSaveHash(userDTO, keyForUser)) return true;
                }
            }
        }

        if (userDTO.getUserId() == null || userDTO.getRole() == null ||
                (userDTO.getRole() != 0 && userDTO.getRole() != 1)) {
            return true;
        }

        if(!BooleanUtil.isTrue(stringRedisTemplate.expire(token, Duration.ofMinutes(LOGIN_TTL)))){
            return true;
        }

        try {
            USER_THREAD_LOCAL.set(userDTO);
        } catch (Exception e) {
            log.error(e.toString());
            USER_THREAD_LOCAL.remove();
        }
        return true;
    }

    public boolean executeScriptSaveHash(UserDTO userDTO, String keyForUser) {
        try {
            executeSaveHashMapLua.executeScript(BeanUtil.beanToMap(userDTO), keyForUser, USER_CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.error("执行脚本失败", e);
            return true;
        }
        return false;
    }
}
