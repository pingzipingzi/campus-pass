package com.example.campuspass.service.support;

import com.example.campuspass.entity.User;
import com.example.campuspass.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.example.campuspass.exception.BusinessException.error;

@Component
@RequiredArgsConstructor
public class UserLock {

    private final UserMapper userMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public User lock(Long userId) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            error("用户不存在");
        }
        return user;
    }
}
