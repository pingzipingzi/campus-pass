package com.example.campuspass.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.campuspass.entity.Activity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


@Mapper
public interface ActivityMapper extends BaseMapper<Activity> {
    @Select("SELECT * FROM tb_activity WHERE activity_id = #{activityId} FOR UPDATE")
    Activity selectForUpdate(@Param("activityId") Long activityId);
}
