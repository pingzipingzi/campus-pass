package com.example.campuspass.service.support;

import com.example.campuspass.entity.Activity;
import com.example.campuspass.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.example.campuspass.exception.BusinessException.error;

@Component
@RequiredArgsConstructor
public class ActivityMutationGuard {
    private final ActivityMapper activityMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public Activity lock(Long activityId) {
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            error("活动不存在");
        }
        return activity;
    }
}
