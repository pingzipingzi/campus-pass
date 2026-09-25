package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.*;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.*;
import com.example.campuspass.service.IRegistrationService;
import com.example.campuspass.service.support.ActivityMutationGuard;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RegistrationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class RegistrationServiceImpl extends ServiceImpl<RegistrationMapper, Registration>
        implements IRegistrationService {
    @Autowired private ActivityMapper activityMapper;
    @Autowired private WaitListMapper waitListMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private ActivityMutationGuard mutationGuard;
    private static final int PAGE_SIZE = 10;

    @Override
    @Transactional(readOnly = true)
    public Result<RegistrationVO> getRegistrationStatus(Long activityId) {
        Long userId = currentUserId();
        Activity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw error("活动不存在");
        }
        Registration registration = findRegistration(activityId, userId);
        WaitList waiting = findWaitList(activityId, userId);
        RegistrationVO vo = registration == null ? new RegistrationVO()
                : BeanUtil.copyProperties(registration, RegistrationVO.class);
        vo.setActivityId(activityId);
        vo.setUserId(userId);
        vo.setWaitStatus(waiting == null ? null : waiting.getStatus());
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStatus() == 2) {
            vo.setStatusDescription("活动已取消");
        } else if (registration != null && registration.getStatus() == 0) {
            vo.setStatusDescription("已报名");
        } else if (waiting != null && waiting.getStatus() == 0) {
            // 定时清理前也不向客户端展示已经截止的候补资格。
            if (!now.isBefore(activity.getRegistrationEndTime())) {
                vo.setWaitStatus(3);
                vo.setStatusDescription("候补已失效");
            } else {
                vo.setStatusDescription("候补中");
            }
        } else if (registration != null) {
            vo.setStatusDescription(registration.getStatus() == 1 ? "已取消报名" : "报名已失效");
        } else if (waiting != null) {
            vo.setStatusDescription(waiting.getStatus() == 2 ? "已取消候补" : "候补已失效");
        } else if (activity.getStatus() != 1) {
            vo.setStatusDescription("活动未发布");
        } else if (now.isBefore(activity.getRegistrationStartTime())) {
            vo.setStatusDescription("报名未开始");
        } else if (!now.isBefore(activity.getRegistrationEndTime())) {
            vo.setStatusDescription("报名已截止");
        } else {
            vo.setStatusDescription(activity.getRemainingCapacity() > 0 ? "未报名且活动未满" : "未报名且活动已满");
        }
        return Result.ok(vo);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> registerActivity(Long activityId) {
        Long userId = currentUserId();
        // 必须先锁活动，再读取报名/候补；两张表的唯一键不能保证跨表互斥。
        Activity activity = mutationGuard.lock(activityId);
        checkActivityRegistrationTime(activity);
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(0).equals(user.getStatus())) {
            throw error("账号不可用");
        }
        Registration registration = findRegistration(activityId, userId);
        if (registration != null && registration.getStatus() == 0) {
            throw error("您已报名，请勿重复报名");
        }
        WaitList waiting = findWaitList(activityId, userId);
        if (waiting != null && waiting.getStatus() == 0) {
            throw error("您已在候补队列中");
        }

        // 即使出现历史遗留空位，也优先安排已经排队的人。
        promoteAvailable(activity);
        if (activity.getRemainingCapacity() > 0) {
            activateRegistration(activityId, userId);
            takeSeat(activity);
            return Result.ok();
        }
        checkActivityRegistrationTime(activity);
        enqueue(activityId, userId, waiting);
        return Result.ok("报名人数已满，已加入候补队列");
    }

    public static void checkActivityRegistrationTime(Activity activity) {
        if (!Integer.valueOf(1).equals(activity.getStatus())) {
            throw error("活动未发布或已取消");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getRegistrationStartTime())) {
            throw error("活动报名未开始");
        }
        if (!now.isBefore(activity.getRegistrationEndTime())) {
            throw error("活动报名已截止");
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> cancelRegistration(Long activityId) {
        Long userId = currentUserId();
        Activity activity = mutationGuard.lock(activityId);
        Registration registration = findRegistration(activityId, userId);
        if (registration == null || registration.getStatus() != 0) {
            throw error("您当前没有成功报名，不能释放名额");
        }
        requireOne(baseMapper.update(null, Wrappers.<Registration>lambdaUpdate()
                .eq(Registration::getRegistrationId, registration.getRegistrationId())
                .eq(Registration::getStatus, 0).set(Registration::getStatus, 1)));
        if (activity.getRemainingCapacity() >= activity.getTotalCapacity()) {
            throw error("活动名额数据异常");
        }
        requireOne(activityMapper.update(null, Wrappers.<Activity>lambdaUpdate()
                .eq(Activity::getActivityId, activityId)
                .apply("remaining_capacity < total_capacity")
                .setSql("remaining_capacity = remaining_capacity + 1")));
        activity.setRemainingCapacity(activity.getRemainingCapacity() + 1);
        // 截止后允许取消，但不再递补；候补已转正记录保留为历史，不改成已取消。
        if (activity.getStatus() == 1 && LocalDateTime.now().isBefore(activity.getRegistrationEndTime())) {
            promoteAvailable(activity);
        } else {
            expireWaiting(activityId);
        }
        return Result.ok();
    }

    private void promoteAvailable(Activity activity) {
        while (activity.getRemainingCapacity() > 0) {
            if (!LocalDateTime.now().isBefore(activity.getRegistrationEndTime())) {
                expireWaiting(activity.getActivityId());
                break;
            }
            WaitList head = waitListMapper.selectOne(Wrappers.<WaitList>lambdaQuery()
                    .eq(WaitList::getActivityId, activity.getActivityId()).eq(WaitList::getStatus, 0)
                    .orderByAsc(WaitList::getJoinTime, WaitList::getWaitId).last("LIMIT 1"));
            if (head == null) {
                break;
            }
            User candidate = userMapper.selectById(head.getUserId());
            if (candidate == null || !Integer.valueOf(0).equals(candidate.getStatus())) {
                requireOne(waitListMapper.update(null, Wrappers.<WaitList>lambdaUpdate()
                        .eq(WaitList::getWaitId, head.getWaitId()).eq(WaitList::getStatus, 0)
                        .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null)));
                continue;
            }
            activateRegistration(activity.getActivityId(), head.getUserId());
            LocalDateTime promotedTime = LocalDateTime.now();
            if (promotedTime.isBefore(head.getJoinTime())) {
                promotedTime = head.getJoinTime();
            }
            requireOne(waitListMapper.update(null, Wrappers.<WaitList>lambdaUpdate()
                    .eq(WaitList::getWaitId, head.getWaitId()).eq(WaitList::getStatus, 0)
                    .set(WaitList::getStatus, 1).set(WaitList::getPromotedTime, promotedTime)));
            takeSeat(activity);
        }
    }

    private void activateRegistration(Long activityId, Long userId) {
        Registration existing = findRegistration(activityId, userId);
        if (existing == null) {
            Registration registration = new Registration();
            registration.setActivityId(activityId);
            registration.setUserId(userId);
            registration.setStatus(0);
            requireOne(baseMapper.insert(registration));
        } else {
            requireOne(baseMapper.update(null, Wrappers.<Registration>lambdaUpdate()
                    .eq(Registration::getRegistrationId, existing.getRegistrationId())
                    .in(Registration::getStatus, 1, 2).set(Registration::getStatus, 0)));
        }
    }

    private void enqueue(Long activityId, Long userId, WaitList existing) {
        LocalDateTime joinTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        WaitList tail = waitListMapper.selectOne(Wrappers.<WaitList>lambdaQuery()
                .eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus, 0)
                .orderByDesc(WaitList::getJoinTime, WaitList::getWaitId).last("LIMIT 1"));
        // 重入队复用 wait_id，因此同一时刻也必须排在当前队尾之后。
        if (tail != null && !joinTime.isAfter(tail.getJoinTime())) {
            joinTime = tail.getJoinTime().plusNanos(1_000);
        }
        if (existing == null) {
            WaitList waiting = new WaitList();
            waiting.setActivityId(activityId);
            waiting.setUserId(userId);
            waiting.setStatus(0);
            waiting.setJoinTime(joinTime);
            requireOne(waitListMapper.insert(waiting));
        } else {
            requireOne(waitListMapper.update(null, Wrappers.<WaitList>lambdaUpdate()
                    .eq(WaitList::getWaitId, existing.getWaitId()).ne(WaitList::getStatus, 0)
                    .set(WaitList::getStatus, 0).set(WaitList::getJoinTime, joinTime)
                    .set(WaitList::getPromotedTime, null)));
        }
    }

    private void expireWaiting(Long activityId) {
        waitListMapper.update(null, Wrappers.<WaitList>lambdaUpdate()
                .eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus, 0)
                .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null));
    }

    private void takeSeat(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        requireOne(activityMapper.update(null, Wrappers.<Activity>lambdaUpdate()
                .eq(Activity::getActivityId, activity.getActivityId())
                .eq(Activity::getStatus, 1).gt(Activity::getRemainingCapacity, 0)
                .le(Activity::getRegistrationStartTime, now)
                .gt(Activity::getRegistrationEndTime, now)
                .setSql("remaining_capacity = remaining_capacity - 1")));
        activity.setRemainingCapacity(activity.getRemainingCapacity() - 1);
    }

    private Registration findRegistration(Long activityId, Long userId) {
        return baseMapper.selectOne(Wrappers.<Registration>lambdaQuery()
                .eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId));
    }

    private WaitList findWaitList(Long activityId, Long userId) {
        return waitListMapper.selectOne(Wrappers.<WaitList>lambdaQuery()
                .eq(WaitList::getActivityId, activityId).eq(WaitList::getUserId, userId));
    }

    @Override
    public Result<PageResultVO<RegistrationVO>> pageMyRegistration(Integer pages) {
        if (pages == null || pages < 1) {
            throw error("页码必须大于零");
        }
        Page<Registration> result = lambdaQuery().eq(Registration::getUserId, currentUserId())
                .orderByDesc(Registration::getRegistrationId).page(new Page<>(pages, PAGE_SIZE));
        List<RegistrationVO> list = result.getRecords().stream().map(record -> {
            RegistrationVO vo = BeanUtil.copyProperties(record, RegistrationVO.class);
            vo.setStatusDescription(switch (record.getStatus()) {
                case 0 -> "已报名";
                case 1 -> "已取消报名";
                default -> "报名已失效";
            });
            return vo;
        }).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages(), list));
    }

    private static Long currentUserId() {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null || user.getUserId() == null) {
            throw error("用户未登录");
        }
        return user.getUserId();
    }

    private static void requireOne(int affected) {
        if (affected != 1) {
            throw error("数据状态已变化，请重试");
        }
    }

    private static BusinessException error(String message) {
        return new BusinessException(40000, message, HttpStatus.BAD_REQUEST);
    }
}
