package com.example.campuspass.utils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.mapper.ActivityMapper;
import com.example.campuspass.service.IWaitListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpireWaitListUtils {
    private final ActivityMapper activityMapper;
    private final IWaitListService waitListService;

    @Scheduled(fixedDelay = 60_000)
    public void solve() {
        // 每个活动独立事务；捕获异常发生在事务代理之外，不会吞掉回滚。
        for (Activity activity : activityMapper.selectList(Wrappers.<Activity>lambdaQuery()
                .and(q -> q.eq(Activity::getStatus, 2)
                        .or().le(Activity::getRegistrationEndTime, LocalDateTime.now()))
                .select(Activity::getActivityId))) {
            try {
                waitListService.expireForActivity(activity.getActivityId());
            } catch (Exception e) {
                log.error("处理活动 {} 的过期候补失败", activity.getActivityId(), e);
            }
        }
    }
}
