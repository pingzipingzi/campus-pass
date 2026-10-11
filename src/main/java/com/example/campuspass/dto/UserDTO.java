package com.example.campuspass.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain=true)
public class UserDTO {

    private Long userId;

    private String userName;

    private Integer role;

    private String studentId;

    private String phone;

    private Integer status;

    private LocalDateTime createTime;

    private Long authVersion;

}
