package com.example.campuspass.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {

    private Long userId;

    private String userName;

    private String studentId;

    private String phone;

    private Integer role;

    private Integer status;

    private LocalDateTime createTime;

}
