package com.example.campuspass.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordDTO {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
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
    private String newPassword;


}
