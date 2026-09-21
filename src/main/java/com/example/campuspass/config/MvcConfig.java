package com.example.campuspass.config;

import com.example.campuspass.utils.JwtUtils;
import com.example.campuspass.utils.OuterInterceptor;
import com.example.campuspass.utils.RefreshInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OuterInterceptor())
                .excludePathPatterns(
                        "/auth/**"
                ).order(1);

        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate,jwtUtils)).addPathPatterns("/**").order(0);
    }

}
