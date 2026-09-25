package com.example.campuspass.service;

import com.example.campuspass.dto.*;
import com.example.campuspass.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

public interface IAuthService {
    public Result<Void> sendCode(String phone);

    Result<LoginVO> loginWithCode(CodeLoginDTO codeLoginDTO);

    Result<LoginVO> loginWithPassword(@Valid PasswordLoginDTO passwordLoginDTO);

    Result<Void> signUp(SignUpDTO signUpDTO);

    Result<Void> logOut(HttpServletRequest request);

    Result<Void> changePassword(@Valid ChangePasswordDTO changePasswordDTO);

    Result<Void> forgetPassword(@Valid ForgetPasswordDTO forgetPasswordDTO);
}
