package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.campuspass.dto.ActivityDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.ActivityMapper;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.mapper.RegistrationMapper;
import com.example.campuspass.mapper.WaitListMapper;
import com.example.campuspass.service.support.ActivityMutationGuard;
import com.example.campuspass.utils.enums.ActivityTypeEnum;
import com.example.campuspass.vo.ActivityPageVO;
import com.example.campuspass.vo.ActivityVO;
import com.example.campuspass.vo.PageResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.campuspass.utils.MybatisPlusConstants.PAGE_SIZE_ACTIVITY;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    @Autowired
    private ActivityMutationGuard mutationGuard;

    @Autowired
    private RegistrationMapper registrationMapper;

    @Autowired
    private WaitListMapper waitListMapper;

    @Override
    public Result<Void> createActivity(ActivityDTO activityDTO) {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if(user==null){
            throw new BusinessException(40000, "用户未登录", HttpStatus.BAD_REQUEST);
        }
        Activity activity = BeanUtil.copyProperties(activityDTO, Activity.class);
        Long userId = user.getUserId();
        activity.setCreatorId(userId);
        LocalDateTime now = LocalDateTime.now();
        activity.setCreateTime(now);
        activity.setUpdateTime(now);
        activity.setStatus(0);
        activity.setRemainingCapacity(activity.getTotalCapacity());

        save(activity);
        return Result.ok();
    }

    @Override
    public Result<ActivityVO> getActivityDetail(Long activityId) {
        Activity activity = lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if (activity == null||activity.getStatus()!=1) {
            throw new BusinessException(40000, "活动不存在", HttpStatus.BAD_REQUEST);
        }
        String activityTypeName = ActivityTypeEnum.getDescription(activity.getActivityType());
        ActivityVO activityVO = BeanUtil.copyProperties(activity, ActivityVO.class);
        activityVO.setActivityTypeName(activityTypeName);
        return Result.ok(activityVO);
    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(Integer pages) {

        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        Page<Activity> result = lambdaQuery().eq(Activity::getStatus, 1).orderByAsc(Activity::getActivityId)
                .page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream()
                .map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();


        return Result.ok(new PageResultVO<ActivityPageVO>(result.getTotal()
                , result.getCurrent(), result.getSize(), result.getPages(),pageVOList));


    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> publishActivity(Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        checkOwner(activity);
        if (!activity.getStatus().equals(0)) {
            throw new BusinessException(40000, "活动处于非草稿状态", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(activity.getRegistrationEndTime())) {
            throw new BusinessException(40000, "报名截止时间不能在发布时间之前", HttpStatus.BAD_REQUEST);
        }
        boolean success = lambdaUpdate().eq(Activity::getActivityId, activityId)
                .gt(Activity::getRegistrationEndTime, LocalDateTime.now())
                .eq(Activity::getStatus, 0).set(Activity::getStatus, 1L).update();
        if (!success) {
            throw new BusinessException(40000, "更新失败", HttpStatus.BAD_REQUEST);
        }

        return Result.ok();
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> cancelActivity(Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        checkOwner(activity);
        cancelLockedActivity(activity);
        return Result.ok();
    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> getMyActivityPageList(Integer pages) {
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if(user==null){
            throw new BusinessException(40000, "用户未登录", HttpStatus.BAD_REQUEST);
        }
        Long userId = user.getUserId();
        Page<Activity> result= lambdaQuery().eq(Activity::getCreatorId, userId).orderByDesc(Activity::getActivityId).page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream().map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(),result.getCurrent()
                ,result.getSize(),result.getPages(),pageVOList));
    }

    @Override
    public Result<ActivityVO> getMyActivityDetail(Long activityId) {
        Activity activity = checkUserID(activityId);

        String activityTypeName = ActivityTypeEnum.getDescription(activity.getActivityType());
        ActivityVO activityVO = BeanUtil.copyProperties(activity, ActivityVO.class);
        activityVO.setActivityTypeName(activityTypeName);
        return Result.ok(activityVO);

    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> pageAllActivitiesForAdmin(Integer pages) {
        checkIfAdmin();
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        Page<Activity> result = lambdaQuery().orderByDesc(Activity::getActivityId).page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream()
                .map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();
        return Result.ok(new PageResultVO<>
                (result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), pageVOList));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> forceCancelActivityForAdmin(Long activityId) {
        checkIfAdmin();
        cancelLockedActivity(mutationGuard.lock(activityId));
        return Result.ok();
    }

    private void cancelLockedActivity(Activity activity) {
        if (activity.getStatus() == 2) {
            return;
        }
        boolean success = lambdaUpdate().eq(Activity::getActivityId, activity.getActivityId())
                .set(Activity::getStatus, 2).set(Activity::getRemainingCapacity, activity.getTotalCapacity())
                .update();
        if (!success) {
            throw new BusinessException(40000, "活动取消失败", HttpStatus.BAD_REQUEST);
        }
        // 没有报名或候补时更新零行是正常情况；SQL 异常仍会使整个事务回滚。
        waitListMapper.update(null, Wrappers.<WaitList>lambdaUpdate()
                .eq(WaitList::getActivityId, activity.getActivityId()).eq(WaitList::getStatus, 0)
                .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null));
        registrationMapper.update(null, Wrappers.<Registration>lambdaUpdate()
                .eq(Registration::getActivityId, activity.getActivityId()).eq(Registration::getStatus, 0)
                .set(Registration::getStatus, 2));
    }

    public static void checkIfAdmin() {
        User user =BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null || !Integer.valueOf(1).equals(user.getRole())) {
            throw new BusinessException(40000, "用户不具备管理员权限", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> updateActivityDraft(ActivityDTO activityDTO,Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        checkOwner(activity);
        if (!activity.getStatus().equals(0)) {
            throw new BusinessException(40000, "活动处于非草稿状态", HttpStatus.BAD_REQUEST);
        }
        Integer newTotalCapacity= activityDTO.getTotalCapacity();
        Integer remainingCapacity=activity.getRemainingCapacity();
        Integer oldTotalCapacity=activity.getTotalCapacity();
        if (newTotalCapacity < oldTotalCapacity - remainingCapacity) {
            throw new BusinessException(40000, "修改后活动容量不足", HttpStatus.BAD_REQUEST);
        }
        Activity newActivity = BeanUtil.copyProperties(activityDTO, Activity.class);
        newActivity.setActivityId(activityId);
        newActivity.setTotalCapacity(newTotalCapacity);
        newActivity.setRemainingCapacity(newTotalCapacity + remainingCapacity - oldTotalCapacity);
        boolean success =lambdaUpdate().eq(Activity::getActivityId,activityId)
                .eq(Activity::getStatus,0).update(newActivity);
        if(!success){
            throw new BusinessException(40000, "更新活动失败", HttpStatus.BAD_REQUEST);
        }

        return Result.ok();
    }

    private Activity checkUserID(Long activityId) {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if(user==null){
            throw new BusinessException(40000, "用户未登录", HttpStatus.BAD_REQUEST);
        }
        Activity activity = lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if(activity==null){
            throw new BusinessException(40000, "活动不存在", HttpStatus.BAD_REQUEST);
        }
        checkOwner(activity);
        return activity;
    }

    private void checkOwner(Activity activity) {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null) {
            throw new BusinessException(40000, "用户未登录", HttpStatus.BAD_REQUEST);
        }
        if (!activity.getCreatorId().equals(user.getUserId())) {
            throw new BusinessException(40000, "登录用户与创建活动用户不匹配", HttpStatus.BAD_REQUEST);
        }
    }
}
