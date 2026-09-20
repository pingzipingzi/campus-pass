package com.example.campuspass.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.entity.User;
import com.example.campuspass.mapper.UserMapper;
import com.example.campuspass.service.IUserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
}
