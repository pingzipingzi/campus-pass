package com.example.campuspass.controller;

import com.example.campuspass.dto.ActivityDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.vo.ActivityPageVO;
import com.example.campuspass.vo.ActivityVO;
import com.example.campuspass.vo.PageResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name="活动管理",description = "活动相关接口")
@SecurityRequirement(name="bearerAuth")
@RestController
@RequestMapping("/activities")
public class ActivityController {


    @Autowired
    private IActivityService activityService;

    @Operation(summary="创建活动")
    @PostMapping
    public Result<Void> createActivity(@Valid @RequestBody ActivityDTO activityDTO) {
        return activityService.createActivity(activityDTO);
    }

    @GetMapping("/{activityId}")
    public Result<ActivityVO> getActivityDetail(@PathVariable("activityId") Long activityId) {
        return activityService.getActivityDetail(activityId);
    }
    @GetMapping("/{activityId}/mine")
    public Result<ActivityVO> getMyActivityDetail(@PathVariable("activityId") Long activityId){
        return activityService.getMyActivityDetail(activityId);
    }

    @GetMapping
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList( @Min(1) @RequestParam("pages") Integer pages) {

        return activityService.getActivityPageList(pages);
    }

    @GetMapping("/mine")
    public Result<PageResultVO<ActivityPageVO>> getMyActivityPageList(@Min(1) @RequestParam("pages") Integer pages) {
        return activityService.getMyActivityPageList(pages);
    }

    @PutMapping("/activityId/{activityId}")
    public Result<Void> updateActivityDraft(@Valid @RequestBody ActivityDTO activityDTO
            , @NotNull @PathVariable("activityId") Long activityId) {
        return activityService.updateActivityDraft(activityDTO, activityId);
    }

    @PostMapping("/{activityId}/publish")
    public Result<Void> publishActivity(@PathVariable("activityId") Long activityId) {
        return activityService.publishActivity(activityId);
    }

    @PostMapping("/{activityId}/cancel")
    public Result<Void> cancelActivity(@PathVariable("activityId") Long activityId) {
        return activityService.cancelActivity(activityId);
    }

    @GetMapping("/activityType/{activityType}")
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(@Min(0) @Max(5) @PathVariable("activityType") Integer activityType,
                                                                    @Min(1) @RequestParam("pages") Integer pages) {
        return activityService.getActivityPageList(activityType, pages);
    }

    @GetMapping("/keyword")
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(@RequestParam("keyword") String keyword, @Min(1) @RequestParam("pages") Integer pages) {
        return activityService.getActivityPageList(keyword, pages);
    }

    @GetMapping("/keyword/{activityType}")
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(@Min(0) @Max(5) @PathVariable("activityType") Integer activityType,
                                                                    @RequestParam("keyword") String keyword, @Min(1) @RequestParam("pages") Integer pages) {
        return activityService.getActivityPageList(activityType,keyword, pages);
    }

}
