package com.example.campuspass.service;

import com.example.campuspass.dto.CodeLoginDTO;
import com.example.campuspass.dto.PasswordLoginDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.SignUpDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.net.http.HttpRequest;

public interface IAuthService {
    public Result<Void> sendCode(String phone);

    Result<String> loginWithCode(CodeLoginDTO codeLoginDTO);

    Result<String> loginWithPassword(@Valid PasswordLoginDTO passwordLoginDTO);

    Result<Void> signUp(SignUpDTO signUpDTO);

    Result<Void> logOut(HttpServletRequest request);
}
