package com.example.campuspass.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpDTO {
    @NotBlank(message = "电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不匹配")
    private String phone;
    private String userName;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度必须为6~32")
    @Pattern(
            regexp = "^[!-~]+$",
            message = "密码只能包含英文字母、数字和英文符号，不能包含空格"
    )
    @Pattern(
            regexp = "^(?:(?=.*[A-Za-z])(?=.*[0-9])"
                    + "|(?=.*[A-Za-z])(?=.*[^A-Za-z0-9])"
                    + "|(?=.*[0-9])(?=.*[^A-Za-z0-9])).*$",
            message = "密码至少包含字母、数字、符号中的两种"
    )
    private String password;
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须是六位数字")
    private String code;
    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^20[12]\\d{8}$")
    private String studentId;

}
