package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.ActivityDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.vo.ActivityPageVO;
import com.example.campuspass.vo.ActivityVO;
import com.example.campuspass.vo.PageResultVO;
import jakarta.validation.constraints.NotNull;


public interface IActivityService extends IService<Activity> {
    Result<Void> createActivity( ActivityDTO activityDTO);

    Result<ActivityVO> getActivityDetail(Long activityId);

    Result<PageResultVO<ActivityPageVO>> getActivityPageList(Integer pages);

    Result<Void> updateActivityDraft(ActivityDTO activityDTO,Long activityId);

    Result<Void> publishActivity(@NotNull Long activityId);

    Result<Void> cancelActivity(@NotNull Long activityId);

    Result<PageResultVO<ActivityPageVO>> getMyActivityPageList(Integer pages);

    Result<ActivityVO> getMyActivityDetail(Long activityId);

    Result<PageResultVO<ActivityPageVO>> pageAllActivitiesForAdmin(Integer pages);

    Result<Void> forceCancelActivityForAdmin(Long activityId);
}
