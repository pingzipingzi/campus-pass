package com.example.campuspass.utils;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LimitRateUtils {
    private static final DefaultRedisScript<Long> LIMIT_RATE_SCRIPT;

    @Autowired
    private  StringRedisTemplate stringRedisTemplate;

    static {
        LIMIT_RATE_SCRIPT = new DefaultRedisScript<>();
        LIMIT_RATE_SCRIPT.setLocation(new ClassPathResource("lua/limit_rate.lua"));
        LIMIT_RATE_SCRIPT.setResultType(Long.class);
    }

    public  Long execute(String key,Long limitTimes,Long windowSeconds ){
        return stringRedisTemplate.execute(LIMIT_RATE_SCRIPT,
                List.of(key), String.valueOf(limitTimes), String.valueOf(windowSeconds)
        );
    }

}
