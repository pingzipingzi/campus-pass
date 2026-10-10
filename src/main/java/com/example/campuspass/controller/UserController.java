package com.example.campuspass.controller;

import com.example.campuspass.dto.ChangePasswordDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.service.IAuthService;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "用户管理")
@RequestMapping("/user")
public class UserController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IAuthService authService;

    @GetMapping("/me")
    public Result<UserVO> getMyProfile() {
        return userService.getMyProfile();
    }

    @PatchMapping("/me")
    public Result<Void> updateMyNickName(@RequestParam("name") String name) {
        return userService.updateMyNickName(name);
    }

    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        return authService.changePassword(changePasswordDTO);
    }


}
