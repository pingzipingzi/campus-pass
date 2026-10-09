package com.example.campuspass.controller;

import com.example.campuspass.dto.Result;
import com.example.campuspass.service.IRegistrationService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RegistrationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/registration")
@Tag(name="报名表管理")
public class RegistrationController {

    @Autowired
    private IRegistrationService registrationService;

    @PostMapping("/{activityId}")
    public Result<String> registerActivity(@PathVariable("activityId") Long activityId) {
        return registrationService.registerActivity(activityId);
    }

    @PostMapping("/cancel/{activityId}")
    public Result<Void> cancelRegistration(@PathVariable("activityId") Long activityId) {
        return registrationService.cancelRegistration(activityId);
    }

    @GetMapping("/mine")
    public Result<PageResultVO<RegistrationVO>> pageMyRegistration(@RequestParam("pages") Integer pages) {
        return registrationService.pageMyRegistration(pages);
    }

    @Operation(description = "查看某活动对于当前用户处于什么状态，1.已报名 2.已取消 3.候补中 4.未报名 5 活动已满并且未候补")
    @GetMapping("/status/{activityId}")
    public Result<RegistrationVO> getRegistrationStatus(@PathVariable("activityId") Long activityId) {
        return registrationService.getRegistrationStatus(activityId);
    }



}
