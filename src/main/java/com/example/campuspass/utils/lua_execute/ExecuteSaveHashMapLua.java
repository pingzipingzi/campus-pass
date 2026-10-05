package com.example.campuspass.utils.lua_execute;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.example.campuspass.exception.BusinessException.error;

@Component
@Slf4j
public class ExecuteSaveHashMapLua {

    @Autowired
    private   StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SAVE_HASH_MAP;

    private static final DefaultRedisScript<Long> REGISTRATION_CACHE_INITIALIZATION;

    static {
        SAVE_HASH_MAP = new DefaultRedisScript<>();
        SAVE_HASH_MAP.setResultType(Long.class);
        SAVE_HASH_MAP.setLocation(new ClassPathResource("lua/save_hash_map.lua"));

        REGISTRATION_CACHE_INITIALIZATION = new DefaultRedisScript<>();
        REGISTRATION_CACHE_INITIALIZATION.setResultType(Long.class);
        REGISTRATION_CACHE_INITIALIZATION.setLocation(new ClassPathResource("lua/registration_cache_initialization.lua"));
    }

    public void executeScript(Map<String, Object> map, String key, Long ttl) {

        List<String> list = new ArrayList<>();
        list.add(ttl.toString());
        map.forEach((keys,value)->{
            list.add(keys);
            list.add(value.toString());
        });

        Long result = stringRedisTemplate.execute(
                SAVE_HASH_MAP,
                Collections.singletonList(key),
                list.toArray()
        );
        if(result==-1){
            error("传入参数有误");
        }
    }

    public void executeInitialization(Map<String, Object> map, String key, Long ttl) {

        List<String> list = new ArrayList<>();
        list.add(ttl.toString());
        map.forEach((keys,value)->{
            list.add(keys);
            list.add(value.toString());
        });

        Long result = stringRedisTemplate.execute(
                REGISTRATION_CACHE_INITIALIZATION,
                Collections.singletonList(key),
                list.toArray()
        );
        if(result==-1){
            error("传入参数有误");
        }
        if(result==-2){
            log.warn("缓存已存在");
        }
    }
}
