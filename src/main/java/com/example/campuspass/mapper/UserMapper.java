package com.example.campuspass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campuspass.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
