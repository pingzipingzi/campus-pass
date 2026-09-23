package com.example.campuspass.config;

import com.example.campuspass.service.IUserService;
import com.example.campuspass.utils.JwtUtils;
import com.example.campuspass.utils.InnerInterceptor;
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

    @Autowired
    private IUserService userService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new InnerInterceptor())
                .excludePathPatterns(
                        "/auth/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                ).order(1);

        registry.addInterceptor(new RefreshInterceptor(stringRedisTemplate,jwtUtils,userService)).addPathPatterns("/**").order(0);
    }

}
