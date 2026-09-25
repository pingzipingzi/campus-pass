package com.example.campuspass.utils.lua_execute;

import com.example.campuspass.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class ExecuteSaveHashMapLua {

    @Autowired
    private   StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> SAVE_HASH_MAP;



    static {
        SAVE_HASH_MAP = new DefaultRedisScript<>();
        SAVE_HASH_MAP.setResultType(Long.class);
        SAVE_HASH_MAP.setLocation(new ClassPathResource("lua/save_hash_map.lua"));

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
            throw new BusinessException(40000, "传入参数有误", HttpStatus.BAD_REQUEST);
        }
    }
}
