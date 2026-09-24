package com.example.campuspass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.cglib.core.Local;

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
