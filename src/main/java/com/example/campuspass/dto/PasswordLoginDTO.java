package com.example.campuspass.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PasswordLoginDTO {

    @NotBlank(message = "电话号码不能为空")
    private String phone;
    @NotBlank(message = "密码不能为空")
    private String password;
}
