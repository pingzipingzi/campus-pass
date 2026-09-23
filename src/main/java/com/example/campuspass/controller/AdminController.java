package com.example.campuspass.controller;

import com.example.campuspass.dto.ActivityDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.vo.ActivityPageVO;
import com.example.campuspass.vo.ActivityVO;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name="管理员模块",description = "管理员管理用户，管理活动")
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private IUserService userService;

    @Autowired
    private IActivityService activityService;

    @GetMapping("/users")
    public Result<PageResultVO<UserVO>> pageAllUsers(@Min(1)@RequestParam("pages")Integer pages){
        return userService.PageAllUsersForAdmin(pages);
    }

    @PostMapping("/disable/{userId}")
    public Result<Void> disableUser(@PathVariable("userId") Long userId) {
        return userService.disableUserForAdmin(userId);
    }

    @PostMapping("/enable/{userId}")
    public Result<Void> enableUser(@PathVariable("userId") Long userId) {
        return userService.enableUserForAdmin(userId);
    }

    @GetMapping("/activity")
    public Result<PageResultVO<ActivityPageVO>> pageAllActivities(@Min(1)@RequestParam("pages")Integer pages) {
        return activityService.pageAllActivitiesForAdmin(pages);
    }

    @PostMapping("/activity/cancel/{activityId}")
    public Result<Void> forceCancelActivity(@PathVariable("activityId") Long activityId) {
        return activityService.forceCancelActivityForAdmin(activityId);
    }

}
