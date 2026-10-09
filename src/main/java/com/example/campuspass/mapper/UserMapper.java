package com.example.campuspass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campuspass.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("select * from tb_user where user_id = #{userId} for update ")
    User getUserById(@Param("userId") Long userId);
}
