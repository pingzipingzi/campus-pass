package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Registration;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.RegistrationMapper;
import com.example.campuspass.service.IActivityService;
import com.example.campuspass.service.IRegistrationService;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RegistrationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class RegistrationServiceImpl extends ServiceImpl<RegistrationMapper, Registration> implements IRegistrationService {

    @Autowired
    private IActivityService activityService;

    @Autowired
    private IWaitListService waitListService;

    private static final Integer REGISTRATION_PAGE_SIZE=10;

    @Override
    public Result<RegistrationVO> getRegistrationStatus(Long activityId) {
        User user = USER_THREAD_LOCAL.get();
        Long userId = user.getUserId();
        Registration registration = lambdaQuery().eq(Registration::getUserId, userId).eq(Registration::getActivityId, activityId).one();
        String statusDescription="";
        if(registration==null){
            Activity activity = activityService.lambdaQuery().eq(Activity::getActivityId, activityId).one();
            Integer remainingCapacity = activity.getRemainingCapacity();
            statusDescription = remainingCapacity > 0 ? "未报名且活动未满" : "未报名且活动已满";
        } else if (Integer.valueOf(0).equals(registration.getStatus())) {
            statusDescription="已报名";
        } else if (Integer.valueOf(1).equals((registration.getStatus()))) {
            statusDescription = "已取消";
        }

        RegistrationVO registrationVO = BeanUtil.copyProperties(registration, RegistrationVO.class);
        registrationVO.setStatusDescription(statusDescription);
        return Result.ok(registrationVO);
    }

    @Override
    @Transactional
    public Result<Void> registerActivity(Long activityId) {
        User user = USER_THREAD_LOCAL.get();
        Long userId = user.getUserId();
        Registration registration = lambdaQuery().eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId).one();
        if (registration != null && registration.getStatus() == 1) {
            throw new BusinessException(40000, "用户已经报名过了", HttpStatus.BAD_REQUEST);
        }
        registration = new Registration();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        Activity activity = activityService.lambdaQuery().eq(Activity::getActivityId, activityId)
                .eq(Activity::getStatus, 1).one();
        if (activity== null) {
            throw new BusinessException(40000, "活动不存在", HttpStatus.BAD_REQUEST);
        }
        checkActivityRegistrationTime(activity);

        int status = activity.getRemainingCapacity() > 0 ? 0 : 2;
        registration.setStatus(status);
        if(status==0){
            boolean success = activityService.lambdaUpdate().eq(Activity::getActivityId, activityId)
                    .setSql("remaining_capacity = remaining_capacity - 1")
                    .gt(Activity::getRemainingCapacity, 0).update();
            if(!success){
                throw new BusinessException(40000, "更新失败，请尝试重新报名", HttpStatus.BAD_REQUEST);
            }
        }else{
            WaitList waitList = new WaitList();
            waitList.setActivityId(activityId);
            waitList.setUserId(userId);
            waitList.setStatus(0);
            boolean save = waitListService.save(waitList);

            boolean save1 = save(registration);
            if (!save || !save1) {
                throw new BusinessException(40000, "更新失败，请尝试重新报名", HttpStatus.BAD_REQUEST);
            }
            return Result.ok("报名人数已满，已自动帮您加入候补队列");
        }

        save(registration);
        return Result.ok();
    }

    public static void checkActivityRegistrationTime(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime registrationStartTime = activity.getRegistrationStartTime();
        if (now.isBefore(registrationStartTime)) {
            throw new BusinessException(40000, "活动报名未开始", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime registrationEndTime = activity.getRegistrationEndTime();
        if (now.isAfter(registrationEndTime)) {
            throw new BusinessException(40000, "活动报名已截止", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    @Transactional
    public Result<Void> cancelRegistration(Long activityId) {
        User user = USER_THREAD_LOCAL.get();
        Long userId = user.getUserId();
        Registration registration = lambdaQuery().eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId)
                .eq(Registration::getStatus, 0).one();
        if (registration == null) {
            throw new BusinessException(40000, "用户还没有报名此活动", HttpStatus.BAD_REQUEST);
        }

        Activity activity = activityService.lambdaQuery().eq(Activity::getActivityId, activityId).one();
        if (activity== null) {
            throw new BusinessException(40000, "活动不存在", HttpStatus.BAD_REQUEST);
        }

        boolean success = lambdaUpdate().eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId).set(Registration::getStatus, 1).update();

        boolean success1 = activityService.lambdaUpdate().eq(Activity::getActivityId, activityId)
                .le(Activity::getRemainingCapacity, activity.getTotalCapacity())
                .setSql("remaining_capacity = remaining_capacity + 1").update();
        WaitList thisOne = waitListService.lambdaQuery().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus, 1).one();
        if(thisOne!=null){
            boolean update = waitListService.lambdaUpdate().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId).set(WaitList::getStatus, 2).update();
            if (!update) {
                throw new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
            }
        }
        if(!success||!success1){
            throw new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
        }

        WaitList one = waitListService.lambdaQuery().eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus,0)
                .orderByAsc(WaitList::getJoinTime).last("limit 1").one();
        if(one==null){
            return Result.ok();
        }

        Long userIdWait = one.getUserId();
        Registration registrationForWait = new Registration();
        registrationForWait.setUserId(userIdWait);
        registrationForWait.setActivityId(activityId);
        registrationForWait.setStatus(1);
        boolean success2 = activityService.lambdaUpdate().eq(Activity::getActivityId, activityId)
                .gt(Activity::getRemainingCapacity, 0).setSql("remaining_capacity = remaining_capacity - 1").update();
        boolean save = save(registration);
        boolean success3 = waitListService.lambdaUpdate().eq(WaitList::getWaitId, one.getWaitId()).eq(WaitList::getStatus,0).set(WaitList::getStatus, 1).update();
        if (!success2||!save||!success3) {
            throw  new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
        }

        return Result.ok();
    }

    @Override
    public Result<PageResultVO<RegistrationVO>> pageMyRegistration(Integer pages) {
        Page<Registration> page = new Page<>(pages, REGISTRATION_PAGE_SIZE);
        User user = USER_THREAD_LOCAL.get();
        Long userId = user.getUserId();
        Page<Registration> result = lambdaQuery().eq(Registration::getUserId, userId).page(page);
        List<RegistrationVO> list = result.getRecords().stream()
                .map(elem -> BeanUtil.copyProperties(elem, RegistrationVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages(), list));


    }


}
