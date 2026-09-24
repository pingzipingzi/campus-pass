package com.example.campuspass.service.support;

import com.example.campuspass.entity.Activity;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 同一活动的写流程统一先锁活动行，再处理报名/候补。
 * 库存仍用原子增减；行锁负责跨表互斥和候补顺序，事务结束自动释放。
 */
@Component
@RequiredArgsConstructor
public class ActivityMutationGuard {
    private final ActivityMapper activityMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public Activity lock(Long activityId) {
        Activity activity = activityMapper.selectForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(40000, "活动不存在", HttpStatus.BAD_REQUEST);
        }
        return activity;
    }
}
