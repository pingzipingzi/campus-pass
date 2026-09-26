package com.example.campuspass.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host}" )String host,
            @Value("${spring.data.redis.port:6379}")int port,
            @Value("${spring.data.redis.database:0}")int database
            ){

        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);

        return Redisson.create(config);
    }
}
