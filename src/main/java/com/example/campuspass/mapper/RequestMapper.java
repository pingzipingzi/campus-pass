package com.example.campuspass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campuspass.entity.Request;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequestMapper extends BaseMapper<Request> {
}
