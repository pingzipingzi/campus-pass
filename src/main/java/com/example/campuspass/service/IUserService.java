package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.User;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;

public interface IUserService extends IService<User> {

    Result<PageResultVO<UserVO>> PageAllUsersForAdmin(Integer pages);

    Result<Void> disableUserForAdmin(Long userId);

    Result<Void> enableUserForAdmin(Long userId);
}
