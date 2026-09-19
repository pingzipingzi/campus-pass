package com.example.campuspass.controller;

import com.example.campuspass.dto.CodeLoginDTO;
import com.example.campuspass.dto.PasswordLoginDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.SignUpDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone")String phone) {
        return Result.ok();
    }

    @PostMapping("/login/code")
    public Result loginWithCode(@RequestBody CodeLoginDTO codeLoginDTO) {
        return Result.ok();
    }

    @PostMapping("/login/password")
    public Result loginWithPassword(@RequestBody PasswordLoginDTO passwordLoginDTO) {
        return Result.ok();
    }

    @PostMapping("/signup")
    public Result signUp(@RequestBody SignUpDTO signUpDTO) {
        return Result.ok();
    }

    @PostMapping("logout")
    public Result logout(){
        return Result.ok();
    }
}
