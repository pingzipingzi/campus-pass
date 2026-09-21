package com.example.campuspass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordLoginDTO {

    @NotBlank(message = "电话号码不能为空")
    private String phone;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度为6~32")
    private String password;
}
