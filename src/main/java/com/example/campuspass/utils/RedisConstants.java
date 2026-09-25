package com.example.campuspass.utils;

public class RedisConstants {
    // 验证码，后接手机号
    public static final String LOGIN_CODE_KEY_PREFIX = "login:code:";

    // 登录会话，后接完整 sessionId
    public static final String LOGIN_STATUS_KEY_PREFIX = "login:status:";

    // 用户资料缓存，后接用户 ID
    public static final String USER_CACHE_KEY_PREFIX = "user:user-id:";
    // 用户资料缓存有效期，单位：秒（2 分钟）
    public static final long USER_CACHE_TTL_SECONDS = 2 * 60L;

    // 各类限流计数，后接手机号
    public static final String SEND_CODE_RATE_LIMIT_KEY_PREFIX = "login:times:send:";
    public static final String PASSWORD_LOGIN_RATE_LIMIT_KEY_PREFIX = "login:times:password:";
    public static final String CODE_VERIFY_RATE_LIMIT_KEY_PREFIX = "login:times:code:";
    public static final String RESET_PASSWORD_RATE_LIMIT_KEY_PREFIX = "forget-password:times:";

    public static final Long LOGIN_TTL=60*12L;
}
