package com.example.campuspass.service.support;

import com.example.campuspass.exception.ErrorCode;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.example.campuspass.exception.BusinessException.error;

@Component
@RequiredArgsConstructor
public class ActivityLock {
    private final ActivityMapper activityMapper;

    @Transactional(propagation = Propagation.MANDATORY)//传播等级是必须在已有事务中运行
    public Activity lock(Long activityId) {
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            error(ErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
        return activity;
    }
}
