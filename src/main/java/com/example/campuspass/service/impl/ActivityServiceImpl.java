package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.ActivityDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.ActivityMapper;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.service.IRegistrationService;
import com.example.campuspass.service.IUserService;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.utils.enums.ActivityTypeEnum;
import com.example.campuspass.vo.ActivityPageVO;
import com.example.campuspass.vo.ActivityVO;
import com.example.campuspass.vo.PageResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.campuspass.utils.MybatisPlusConstants.PAGE_SIZE_ACTIVITY;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    @Autowired
    private IUserService userService;

    @Autowired
    private IRegistrationService registrationService;

    @Autowired
    private IWaitListService waitListService;

    @Override
    public Result<Void> createActivity(ActivityDTO activityDTO) {
        User user = USER_THREAD_LOCAL.get();
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
    public Result<Void> publishActivity(Long activityId) {
        Activity activity = checkUserID(activityId);
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
    @Transactional
    public Result<Void> cancelActivity(Long activityId) {
        checkUserID(activityId);
        boolean success = lambdaUpdate().eq(Activity::getActivityId, activityId).set(Activity::getStatus, 2L).update();
        boolean success1 = waitListService.lambdaUpdate().eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus,0)
                .set(WaitList::getStatus, 3).update();
        registrationService.lambdaUpdate().eq(Registration::getActivityId, activityId)
                .in(Registration::getStatus, 0, 2).set(Registration::getStatus, 3).update();
        if (!success||!success1) {
            throw new BusinessException(40000, "更新失败", HttpStatus.BAD_REQUEST);
        }
        return Result.ok();
    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> getMyActivityPageList(Integer pages) {
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        User user = USER_THREAD_LOCAL.get();
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
    @Transactional
    public Result<Void> forceCancelActivityForAdmin(Long activityId) {
        checkIfAdmin();
        boolean success = lambdaUpdate().eq(Activity::getActivityId, activityId).set(Activity::getStatus, 2).update();
        boolean success1 = waitListService.lambdaUpdate().eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus,0)
                .set(WaitList::getStatus, 3).update();
        registrationService.lambdaUpdate().eq(Registration::getActivityId, activityId)
                .in(Registration::getStatus, 0, 2).set(Registration::getStatus, 3).update();

        if(!success||!success1){
            throw new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
        }
        return Result.ok();
    }

    public static void checkIfAdmin() {
        User user = USER_THREAD_LOCAL.get();
        if (user == null || !Integer.valueOf(1).equals(user.getRole())) {
            throw new BusinessException(40000, "用户不具备管理员权限", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public Result<Void> updateActivityDraft(ActivityDTO activityDTO,Long activityId) {
        Activity activity = checkUserID(activityId);
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
        User user = USER_THREAD_LOCAL.get();
        if(user==null){
            throw new BusinessException(40000, "用户未登录", HttpStatus.BAD_REQUEST);
        }
        Activity activity = lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if(activity==null){
            throw new BusinessException(40000, "活动不存在", HttpStatus.BAD_REQUEST);
        }
        if (!activity.getCreatorId().equals(user.getUserId())) {
            throw new BusinessException(40000, "登录用户与创建活动用户不匹配", HttpStatus.BAD_REQUEST);
        }
        return activity;
    }
}
