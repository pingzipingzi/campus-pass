package com.example.campuspass.utils;

import cn.hutool.core.util.StrUtil;
import com.example.campuspass.exception.BusinessException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.example.campuspass.utils.RedisConstants.CACHE_LOCK_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.CACHE_NOT_FOUND_VALUE;

@Component
public class RedissonUtils {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public String cacheResetForString(String key, Duration ttl, Supplier<String> loader) {
        String lockKey = CACHE_LOCK_KEY_PREFIX + key;

        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired=false;

        String value = null;

        try {
            acquired = lock.tryLock(1,  TimeUnit.SECONDS);
            if (!acquired) {
                error("活动信息加载中，请稍后重试");
            }

            //再次读取缓存，如果别的线程已经重建缓存就返回
            String str = stringRedisTemplate.opsForValue().get(key);
            if (!StrUtil.isBlank(str)) {
                if (CACHE_NOT_FOUND_VALUE.equals(str)) {
                    error("活动不存在");
                }
                return str;
            }

            value=loader.get();

            if (CACHE_NOT_FOUND_VALUE.equals(value)) {
                stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(30L));
                error("活动不存在");
            }

            stringRedisTemplate.opsForValue().set(key, value, ttl);

            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error("请求中断，请稍后重试");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return value;
    }

    public void cacheRemove(String key) {

        RLock lock = redissonClient.getLock(CACHE_LOCK_KEY_PREFIX + key);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(1, TimeUnit.SECONDS);
            if (!acquired) {
                error("请求失败，请稍后重试");
            }

            stringRedisTemplate.delete(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            error("请求中断，请稍后再试");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public static void error(String message) {
        throw new BusinessException(40000, message, HttpStatus.BAD_REQUEST);
    }
}
