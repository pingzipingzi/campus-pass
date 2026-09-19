package com.example.campuspass.dto;

import lombok.Data;

@Data
public class SignUpDTO {
    private String phone;
    private String userName;
    private String password;
    private String code;
    private String studentId;
}
