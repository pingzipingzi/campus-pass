package com.example.campuspass.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignUpDTO {
    @NotBlank(message="电话不能为空")
    private String phone;
    private String userName;
    @NotBlank(message = "密码不能为空")
    private String password;
    @NotBlank(message = "验证码不能为空")
    private String code;
    @NotBlank(message = "学号不能为空")
    private String studentId;
}
