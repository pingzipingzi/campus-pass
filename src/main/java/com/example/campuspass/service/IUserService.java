package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.ChangePasswordDTO;
import com.example.campuspass.dto.ForgetPasswordDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.User;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;
import jakarta.validation.Valid;

public interface IUserService extends IService<User> {

    Result<PageResultVO<UserVO>> PageAllUsersForAdmin(Integer pages);

    Result<Void> disableUserForAdmin(Long userId);

    Result<Void> enableUserForAdmin(Long userId);

    Result<UserVO> getMyProfile();

    Result<Void> updateMyNickName(String name);

}
