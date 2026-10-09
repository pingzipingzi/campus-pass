package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RegistrationVO;

public interface IRegistrationService extends IService<Registration> {
    Result<RegistrationVO> getRegistrationStatus(Long activityId);

    Result<String> registerActivity(Long activityId);

    Result<Void> cancelRegistration(Long activityId);

    Result<PageResultVO<RegistrationVO>> pageMyRegistration(Integer pages);

}
