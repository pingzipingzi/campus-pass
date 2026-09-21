package com.example.campuspass.controller;

import com.example.campuspass.dto.CodeLoginDTO;
import com.example.campuspass.dto.PasswordLoginDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.SignUpDTO;
import com.example.campuspass.entity.User;
import com.example.campuspass.service.impl.AuthServiceImpl;
import com.example.campuspass.vo.LoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.net.http.HttpRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @Autowired
    private AuthServiceImpl authService;

    @PostMapping("/code")
    public Result<Void> sendCode( @RequestParam("phone")String phone) {
        return authService.sendCode(phone);
    }

    @PostMapping("/login/code")
    public Result<String> loginWithCode(@Valid @RequestBody CodeLoginDTO codeLoginDTO) {
        return authService.loginWithCode(codeLoginDTO);
    }

    @PostMapping("/login/password")
    public Result<String> loginWithPassword(@Valid @RequestBody PasswordLoginDTO passwordLoginDTO) {
        return authService.loginWithPassword(passwordLoginDTO);
    }

    @PostMapping("/signup")
    public Result<Void> signUp(@Valid @RequestBody SignUpDTO signUpDTO) {
        return authService.signUp(signUpDTO);
    }

    @PostMapping("logout")
    public Result<Void> logOut(HttpServletRequest request){
        return authService.logOut(request);
    }
}
