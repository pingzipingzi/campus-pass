package com.example.campuspass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;


@Data
@Accessors(chain = true)
@TableName("tb_user")
public class User  {
    @TableId(value="user_id",type= IdType.AUTO)
    private Long userId;
    @TableField("user_name")
    private String userName;

    private String password;

    private String studentId;

    private String phone;

    private Integer role;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long authVersion;

}
