package com.example.campuspass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("tb_registration")
@Data
@Accessors(chain = true)
public class Registration {

    @TableId(value = "registration_id", type = IdType.AUTO)
    private Long registrationId;

    private Long activityId;

    private Long userId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
