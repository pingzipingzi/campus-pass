package com.example.campuspass.utils;


import com.example.campuspass.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

public class InnerInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if(userDTO==null){
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
