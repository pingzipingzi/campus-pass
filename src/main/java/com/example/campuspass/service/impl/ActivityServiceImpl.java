package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.ActivityDTO;
import com.example.campuspass.dto.ActivityOfRegistrationDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.mapper.ActivityMapper;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.service.IRegistrationService;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.service.support.ActivityLock;
import com.example.campuspass.utils.RedissonUtils;
import com.example.campuspass.utils.enums.ActivityTypeEnum;
import com.example.campuspass.utils.lua_execute.ExecuteSaveHashMapLua;
import com.example.campuspass.vo.ActivityPageVO;
import com.example.campuspass.vo.ActivityVO;
import com.example.campuspass.vo.PageResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RedisConstants.ACTIVITY_DETAIL_KEY_PREFIX;
import static com.example.campuspass.utils.RedisConstants.CACHE_NOT_FOUND_VALUE;
import static com.example.campuspass.utils.MybatisPlusConstants.PAGE_SIZE_ACTIVITY;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Slf4j
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {

    @Autowired
    @Lazy
    private ActivityServiceImpl self;

    @Autowired
    private ActivityLock mutationGuard;

    @Autowired
    private IWaitListService waitListService;

    @Autowired
    @Lazy
    private IRegistrationService registrationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonUtils redissonUtils;

    @Autowired
    private ExecuteSaveHashMapLua executeSaveHashMapLua;

    @Override
    public Result<Void> createActivity(ActivityDTO activityDTO) {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if(user==null){
            error("用户未登录");
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
        String key = ACTIVITY_DETAIL_KEY_PREFIX + activityId;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        if (CACHE_NOT_FOUND_VALUE.equals(jsonStr)) {
            error("活动不存在");
        }
        ActivityVO activityVO = JSONUtil.toBean(jsonStr, ActivityVO.class);
        if (StrUtil.isBlank(jsonStr)) {
            String value = redissonUtils.cacheResetForString(key, Duration.ofMinutes(2L)
                    , () -> self.loadJsonStr(activityId));//空括号是指这里的get方法,不需要形参
            if (value == null) {
                error("服务器异常");
            }
            activityVO = JSONUtil.toBean(value, ActivityVO.class);
        }
        return Result.ok(activityVO);
    }

    public String loadJsonStr(Long activityId) {
        Activity activity = lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if (activity == null||activity.getStatus()!=1) {
            return CACHE_NOT_FOUND_VALUE;
        }
        String activityTypeName = ActivityTypeEnum.getDescription(activity.getActivityType());
        ActivityVO activityVO = BeanUtil.copyProperties(activity, ActivityVO.class);
        activityVO.setActivityTypeName(activityTypeName);
        return JSONUtil.toJsonStr(activityVO);
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
            error("活动处于非草稿状态");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(activity.getRegistrationEndTime())) {
            error("报名截止时间不能在发布时间之前");
        }
        boolean success = lambdaUpdate().eq(Activity::getActivityId, activityId)
                .gt(Activity::getRegistrationEndTime, LocalDateTime.now())
                .eq(Activity::getStatus, 0).set(Activity::getStatus, 1L)
                .set(Activity::getRegistrationCacheStatus, 0).update();
        if (!success) {
            error("更新失败");
        }
        removeActivityCacheAfterCommitWhenPublish(activityId);
        return Result.ok();
    }
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> cancelActivity(Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        checkOwner(activity);
        cancelLockedActivity(activity);
        removeActivityCacheAfterCommitWhenCancel(activityId);
        return Result.ok();
    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> getMyActivityPageList(Integer pages) {
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if(user==null){
            error("用户未登录");
        }
        Long userId = user.getUserId();
        Page<Activity> result= lambdaQuery().eq(Activity::getCreatorId, userId).orderByDesc(Activity::getActivityId).page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream().map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(),result.getCurrent()
                ,result.getSize(),result.getPages(),pageVOList));
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
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(String keyword, Integer pages) {
        if(StrUtil.isBlank(keyword)){
            error("关键词不能为空");
        }
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        Page<Activity> result = lambdaQuery().eq(Activity::getStatus, 1).like(Activity::getActivityName, keyword).orderByDesc(Activity::getActivityId).page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream().map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), pageVOList));
    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(Integer activityType, String keyword, Integer pages) {
        if(StrUtil.isBlank(keyword)){
            error("关键词不能为空");
        }
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        Page<Activity> result = lambdaQuery().eq(Activity::getActivityType, activityType).eq(Activity::getStatus, 1).like(Activity::getActivityName, keyword).
                orderByDesc(Activity::getActivityId).page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream().map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), pageVOList));
    }

    @Override
    public Result<PageResultVO<ActivityPageVO>> getActivityPageList(Integer activityType, Integer pages) {
        Page<Activity> page = new Page<>(pages, PAGE_SIZE_ACTIVITY);
        Page<Activity> result = lambdaQuery().eq(Activity::getActivityType, activityType).eq(Activity::getStatus, 1).orderByDesc(Activity::getActivityId).page(page);
        List<ActivityPageVO> pageVOList = result.getRecords().stream().map(elem -> BeanUtil.copyProperties(elem, ActivityPageVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), pageVOList));
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> forceCancelActivityForAdmin(Long activityId) {
        checkIfAdmin();
        cancelLockedActivity(mutationGuard.lock(activityId));
        removeActivityCacheAfterCommitWhenCancel(activityId);
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
            error("活动取消失败");
        }
        // 没有报名或候补时更新零行是正常情况；SQL 异常仍会使整个事务回滚。
        waitListService.lambdaUpdate()
                .eq(WaitList::getActivityId, activity.getActivityId()).eq(WaitList::getStatus, 0)
                .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null).update();
        registrationService.lambdaUpdate()
                .eq(Registration::getActivityId, activity.getActivityId()).eq(Registration::getStatus, 0)
                .set(Registration::getStatus, 2).update();
    }

    public static void checkIfAdmin() {
        User user =BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null || !Integer.valueOf(1).equals(user.getRole())) {
            error("用户不具备管理员权限");
        }
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> updateActivityDraft(ActivityDTO activityDTO,Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        checkOwner(activity);
        if (!activity.getStatus().equals(0)) {
            error("活动处于非草稿状态");
        }
        Integer newTotalCapacity= activityDTO.getTotalCapacity();
        Integer remainingCapacity=activity.getRemainingCapacity();
        Integer oldTotalCapacity=activity.getTotalCapacity();
        if (newTotalCapacity < oldTotalCapacity - remainingCapacity) {
            error("修改后活动容量不足");
        }
        Activity newActivity = BeanUtil.copyProperties(activityDTO, Activity.class);
        newActivity.setActivityId(activityId);
        newActivity.setTotalCapacity(newTotalCapacity);
        newActivity.setRemainingCapacity(newTotalCapacity + remainingCapacity - oldTotalCapacity);
        boolean success =lambdaUpdate().eq(Activity::getActivityId,activityId)
                .eq(Activity::getStatus,0).update(newActivity);
        if(!success){
            error("更新活动失败");
        }

        return Result.ok();
    }

    private Activity checkUserID(Long activityId) {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if(user==null){
            error("用户未登录");
        }
        Activity activity = lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if(activity==null){
            error("活动不存在");
        }
        checkOwner(activity);
        return activity;
    }

    private void checkOwner(Activity activity) {
        User user = BeanUtil.copyProperties(USER_THREAD_LOCAL.get(), User.class);
        if (user == null) {
            error("用户未登录");
        }
        if (!activity.getCreatorId().equals(user.getUserId())) {
            error("登录用户与创建活动用户不匹配");
        }
    }



    public void removeActivityCacheAfterCommitWhenPublish(Long activityId) {
        String key = ACTIVITY_DETAIL_KEY_PREFIX + activityId;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            redissonUtils.cacheRemove(key);
                        } catch (Exception e) {
                            log.error("活动{}提交成功但是缓存删除失败", activityId, e);
                        }

                        try{
                            self.activityCacheInitialization(activityId);
                        }catch (Exception e){
                            log.error("活动{}提交成功但是报名缓存初始化失败",activityId,e);
                        }
                    }

                }
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void activityCacheInitialization(Long activityId) {
        Activity activity = mutationGuard.lock(activityId);
        if (Integer.valueOf(1).equals(activity.getRegistrationCacheStatus())) {
            return;
        }
        if (!Integer.valueOf(1).equals(activity.getStatus())) {
            error("活动状态异常");
        }
        if (Duration.between(LocalDateTime.now(), activity.getRegistrationEndTime()).getSeconds() < 10) {
            error("活动时间不足");
        }
        registerActivityCache(activityId, activity);
        lambdaUpdate().eq(Activity::getActivityId, activityId).set(Activity::getRegistrationCacheStatus, 1).update();
    }

    public void registerActivityCache(Long activityId,Activity activity) {
        long seconds = Duration.between(LocalDateTime.now(), activity.getRegistrationEndTime()).getSeconds();
        ActivityOfRegistrationDTO activityOfRegistrationDTO=new ActivityOfRegistrationDTO();
        activityOfRegistrationDTO.setRemainingCapacity(activity.getRemainingCapacity());

        activityOfRegistrationDTO.setRegistrationStartTime(activity.getRegistrationStartTime()
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());

        activityOfRegistrationDTO.setRegistrationEndTime(activity.getRegistrationEndTime()
                .atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        Map<String, Object> map = BeanUtil.beanToMap(activityOfRegistrationDTO);
        executeSaveHashMapLua.executeInitialization(map, "registration:activity:" + activityId, seconds);
    }

    @Scheduled(fixedDelay = 30L,timeUnit = TimeUnit.SECONDS)
    public void scheduleActivityCacheInitialization() {
        List<Activity> activities = lambdaQuery().eq(Activity::getStatus, 1).eq(Activity::getRegistrationCacheStatus, 0).list();
        for (Activity activity : activities) {
            Long activityId = activity.getActivityId();
            try {
                self.activityCacheInitialization(activityId);
            } catch (Exception e) {
                log.error("活动id {}缓存初始化失败", activityId, e);
            }
        }
    }

    public void removeActivityCacheAfterCommitWhenCancel(Long activityId) {

        String key = ACTIVITY_DETAIL_KEY_PREFIX + activityId;

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            redissonUtils.cacheRemove(key);
                            stringRedisTemplate.delete("registration:activity:" + activityId);
                        } catch (Exception e) {
                            log.error("活动{}提交成功但是缓存删除失败", activityId, e);
                        }
                    }
                }
        );
    }
}
