package com.example.campuspass.utils;

import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.service.IRegistrationService;
import com.example.campuspass.service.IWaitListService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ExpireWaitListUtils {
    @Autowired
    private IActivityService activityService;

    @Autowired
    private IRegistrationService registrationService;

    @Autowired
    private IWaitListService waitListService;

    @Transactional
    @Scheduled(fixedDelay = 60_000)//fixedDelay是执行完了等指定时间，fixedRate是执行开始就等指定时间
    public  void solve(){
        try {
            List<Activity> list = activityService.lambdaQuery().eq(Activity::getStatus,1)
                    .lt(Activity::getRegistrationEndTime, LocalDateTime.now()).select(Activity::getActivityId)
                    .list();
            List<Long> activityIds = list.stream().map(Activity::getActivityId).toList();
            waitListService.lambdaUpdate()
                    .in(WaitList::getActivityId, activityIds).eq(WaitList::getStatus, 0).set(WaitList::getStatus, 3)
                    .update();
            registrationService.lambdaUpdate().in(Registration::getActivityId, activityIds).eq(Registration::getStatus, 2).set(Registration::getStatus, 3).update();
        } catch (Exception e) {
            log.error("定期处理过期候补失败", e);

        }
    }
}
