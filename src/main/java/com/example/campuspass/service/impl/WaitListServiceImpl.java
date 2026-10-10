package com.example.campuspass.service.impl;

import com.example.campuspass.exception.ErrorCode;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.service.support.ActivityLock;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.mapper.WaitListMapper;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.WaitListVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Objects;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Slf4j
@Service
public class WaitListServiceImpl extends ServiceImpl<WaitListMapper, WaitList> implements IWaitListService {

    private static final Integer WAIT_LIST_PAGE_SIZE=10;

    @Autowired
    private ActivityLock mutationGuard;

    @Autowired
    private RequestServiceImpl requestService;

    @Autowired
    @Lazy
    private WaitListServiceImpl self;

    @Lazy
    @Autowired
    private ActivityServiceImpl activityService;

    @Override
    public Result<PageResultVO<WaitListVO>> pageMyWaitLists(Integer pages) {
        if (pages == null || pages < 1) {
            error(ErrorCode.PARAMETER_INVALID, "页码必须大于零");
        }
        Page<WaitList> page = new Page<>(pages, WAIT_LIST_PAGE_SIZE);
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        Page<WaitList> result = lambdaQuery().eq(WaitList::getUserId, user.getUserId()).orderByDesc(WaitList::getWaitId).page(page);
        List<WaitListVO> list =
                result.getRecords().stream()
                        .map(elem -> BeanUtil.copyProperties(elem, WaitListVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal()
                , result.getCurrent(), result.getSize(), result.getPages(), list));
    }

    @Override
    public Result<Void> cancelWaitList(Long activityId) {
        Long userId = USER_THREAD_LOCAL.get().getUserId();
        return self.removeWaitList(activityId, userId,0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public Result<Void> removeWaitList(Long activityId, Long userId,Integer tag) {
        Activity activity = mutationGuard.lock(activityId);
        WaitList one = lambdaQuery().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId).one();
        if (one == null || one.getStatus() != 0) {
            if (tag == 0) {
                error(ErrorCode.NOT_WAITLISTED, "用户目前未处于候补状态");
            }
            return Result.ok();//应对清理候补记录之前，用户在其他用户取消报名时被禁用并清理缓存
        }
        boolean expired = tag == 1 || activity.getStatus() == 2
                || !LocalDateTime.now().isBefore(activity.getRegistrationEndTime());
        boolean success = lambdaUpdate().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId)
                .eq(WaitList::getStatus, 0).set(WaitList::getStatus, expired ? 3 : 2)
                .set(WaitList::getPromotedTime, null).update();

        if (!success) {
            error(ErrorCode.DATA_OPERATION_FAILED, "数据更新失败");
        }
        requestService.removeRequestCacheAfterCommit(activityId, userId, 0);
        return Result.ok();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireForActivity(Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        if (activity.getStatus() == 2
                || !LocalDateTime.now().isBefore(activity.getRegistrationEndTime())) {
            baseMapper.update(null, Wrappers.<WaitList>lambdaUpdate()
                    .eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus, 0)
                    .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null));
        }
    }

    @Override
    public Result<PageResultVO<WaitListVO>> pageWaitListOfMyActivity(Integer pages, Long activityId) {
        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if(userDTO == null) {
            error(ErrorCode.AUTH_REQUIRED, "用户未登录");}
        Long userId = userDTO.getUserId();
        Activity activity = activityService.lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if (activity == null || !Integer.valueOf(1).equals(activity.getStatus())) {
            error(ErrorCode.ACTIVITY_STATE_CONFLICT, "活动状态异常");
        }
        if (!Objects.equals(activity.getCreatorId(), userId) && userDTO.getRole() != 1) {
            error(ErrorCode.FORBIDDEN, "用户权限不足");
        }

        Page<WaitList> page = new Page<>(pages, WAIT_LIST_PAGE_SIZE);
        Page<WaitList> result = lambdaQuery().eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus,0).orderByAsc(WaitList::getJoinTime,WaitList::getWaitId).page(page);
        List<WaitListVO> list = result.getRecords().stream().map(elem -> BeanUtil.copyProperties(elem, WaitListVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), list));
    }
}
