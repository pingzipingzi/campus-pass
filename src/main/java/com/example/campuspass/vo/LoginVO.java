package com.example.campuspass.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private Long userId;
    private String userName;
    private Integer role;
}
