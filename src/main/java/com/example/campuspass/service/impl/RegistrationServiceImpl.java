package com.example.campuspass.service.impl;

import com.example.campuspass.exception.ErrorCode;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.MessageDTO;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.*;
import com.example.campuspass.listener.RegistrationDeadLetterConsumer;
import com.example.campuspass.mapper.RegistrationMapper;
import com.example.campuspass.service.*;
import com.example.campuspass.service.support.ActivityLock;
import com.example.campuspass.service.support.UserLock;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RegistrationVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Slf4j
@Service
public class RegistrationServiceImpl extends ServiceImpl<RegistrationMapper, Registration>
        implements IRegistrationService {
    @Autowired
    private ActivityLock mutationGuard;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private IActivityService activityService;
    @Autowired
    private IWaitListService waitListService;
    @Autowired
    private IUserService userService;
    @Autowired
    private RequestServiceImpl requestService;
    @Autowired
    private UserLock userLock;
    @Autowired
    private RegistrationDeadLetterConsumer registrationDeadLetterConsumer;
    @Autowired
    @Lazy
    private RegistrationServiceImpl self;
    private static final DefaultRedisScript<Long> REGISTER_ACTIVITY_SCRIPT;
    private static final DefaultRedisScript<Long> REGISTER_ACTIVITY_PENDING_SEND_SCRIPT;
    static {
        REGISTER_ACTIVITY_SCRIPT = new DefaultRedisScript<>();
        REGISTER_ACTIVITY_SCRIPT.setResultType(Long.class);
        REGISTER_ACTIVITY_SCRIPT.setLocation(new ClassPathResource("lua/register_activity.lua"));

        REGISTER_ACTIVITY_PENDING_SEND_SCRIPT = new DefaultRedisScript<>();
        REGISTER_ACTIVITY_PENDING_SEND_SCRIPT.setResultType(Long.class);
        REGISTER_ACTIVITY_PENDING_SEND_SCRIPT.setLocation(new ClassPathResource("lua/register_activity_pending_send.lua"));
    }

    private static final int PAGE_SIZE = 10;

    @Override
    @Transactional(readOnly = true)
    public Result<RegistrationVO> getRegistrationStatus(Long activityId) {
        Long userId = currentUserId();
        Activity activity = activityService.getById(activityId);
        if (activity == null) {
            error(ErrorCode.ACTIVITY_NOT_FOUND, "活动不存在");
        }
        Registration registration = lambdaQuery()
                .eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId).one();
        WaitList waiting = waitListService.lambdaQuery()
                .eq(WaitList::getActivityId, activityId).eq(WaitList::getUserId, userId).one();
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
    public Result<String> registerActivity(Long activityId) {
        Long userId = currentUserId();

        User user = userService.getById(userId);
        if (user == null || !Integer.valueOf(0).equals(user.getStatus())) {
            error(ErrorCode.ACCOUNT_DISABLED, "账号不可用");
        }

        String requestId = UUID.randomUUID().toString();

        String activityOfRegistrationKey = "registration:activity:" + activityId;

        String participantsOfActivityKey = "registration:participant:" + activityId + ":" + userId;

        String messageKey = "registration:message:" + requestId;

        String pendingKey = "registration:message:pending";

        int random = ThreadLocalRandom.current().nextInt(0, 30);

        long sendTime = System.currentTimeMillis();

        Long result = stringRedisTemplate.execute(REGISTER_ACTIVITY_SCRIPT,
                List.of(activityOfRegistrationKey, participantsOfActivityKey, messageKey, pendingKey),
                Long.toString(sendTime), requestId, Integer.toString(random), Long.toString(activityId),
                Long.toString(userId));

        if (result == -1) {
            error(ErrorCode.INTERNAL_ERROR, "服务器异常");
        }
        if (result == -2) {
            error(ErrorCode.REGISTRATION_CLOSED, "现在不在报名时间内");
        }
        if (result == 2) {
            error(ErrorCode.ALREADY_REGISTERED, "您已经报名过了");
        }

        MessageDTO messageDTO = new MessageDTO();
        messageDTO.setActivityId(activityId);
        messageDTO.setUserId(userId);
        messageDTO.setRequestId(requestId);
        messageDTO.setSendTime(sendTime);

        if (result == 1) {
            messageDTO.setCacheStatus(1);
            rabbitTemplate.convertAndSend("campuspass.direct", "registerActivity",
                    JSONUtil.toJsonStr(messageDTO));
            return Result.okWithMessage("报名人数已满，已自动帮您提交候补请求",requestId);
        }

        messageDTO.setCacheStatus(0);
        rabbitTemplate.convertAndSend("campuspass.direct", "registerActivity",
                JSONUtil.toJsonStr(messageDTO));
        return Result.okWithMessage("报名请求提交成功",requestId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processMessage(Activity activity, Long userId, String requestId, Integer cacheStatus) {

        Long activityId = activity.getActivityId();

        boolean updateRequest = requestService.lambdaUpdate().eq(Request::getRequestId, requestId).set(Request::getStatus, 1).update();
        if (!updateRequest) {
            error(ErrorCode.SERVICE_BUSY, "服务器异常，请重试");
        }

        Registration registration = lambdaQuery().eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId).one();
        if (registration != null && registration.getStatus() == 0) {
            error(ErrorCode.ALREADY_REGISTERED, "您已经报名过了");
        }

        if (cacheStatus == 1 || !(activity.getRemainingCapacity() > 0)) {
            WaitList candidate = waitListService.lambdaQuery().eq(WaitList::getActivityId, activityId).eq(WaitList::getUserId, userId).one();
            if (candidate == null) {
                candidate = new WaitList();
                candidate.setActivityId(activityId);
                candidate.setUserId(userId);
                candidate.setStatus(0);
                boolean save = waitListService.save(candidate);
                if (!save) {
                    error(ErrorCode.SERVICE_BUSY, "服务器异常，请重试");
                }
            } else {
                if (candidate.getStatus() == 0) {
                    error(ErrorCode.ALREADY_REGISTERED, "您已经报名过了");
                }
                boolean update = waitListService.lambdaUpdate().eq(WaitList::getWaitId, candidate.getWaitId()).set(WaitList::getStatus, 0)
                        .set(WaitList::getJoinTime, LocalDateTime.now()).set(WaitList::getPromotedTime, null).update();
                if (!update) {
                    error(ErrorCode.SERVICE_BUSY, "服务器异常，请重试");
                }
            }
            return;
        }

        boolean update = activityService.lambdaUpdate().eq(Activity::getActivityId, activityId).gt(Activity::getRemainingCapacity, 0)
                .setSql("remaining_capacity =remaining_capacity - 1").update();
        if (!update) {
            error(ErrorCode.SERVICE_BUSY, "服务器异常，请重试");
        }

        if (registration == null) {
            registration = new Registration();
            registration.setActivityId(activityId);
            registration.setUserId(userId);
            registration.setStatus(0);
            boolean save = save(registration);
            if (!save) {
                error(ErrorCode.SERVICE_BUSY, "服务器异常，请重试");
            }
        } else {
            boolean updateRegistration = lambdaUpdate().eq(Registration::getRegistrationId, registration.getRegistrationId()).set(Registration::getStatus, 0).update();
            if (!updateRegistration) {
                error(ErrorCode.SERVICE_BUSY, "服务器异常，请重试");
            }
        }
    }

    @Override
    public Result<Void> cancelRegistration(Long activityId) {

        Long userId = currentUserId();
        return self.removeRegistration(activityId, userId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public Result<Void> removeRegistration(Long activityId, Long userId) {
        Activity activity = mutationGuard.lock(activityId);
        Registration registration = lambdaQuery()
                .eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId).one();
        if (registration == null || registration.getStatus() != 0) {
            error(ErrorCode.NOT_REGISTERED, "用户当前没有成功报名，不能释放名额");
        }

        requireSuccess(lambdaUpdate()
                .eq(Registration::getRegistrationId, registration.getRegistrationId())
                .eq(Registration::getStatus, 0).set(Registration::getStatus, 1).update());
        if (activity.getRemainingCapacity() >= activity.getTotalCapacity()) {
            error(ErrorCode.DATA_INCONSISTENT, "活动名额数据异常");
        }
        requireSuccess(activityService.lambdaUpdate()
                .eq(Activity::getActivityId, activityId)
                .apply("remaining_capacity < total_capacity")
                .setSql("remaining_capacity = remaining_capacity + 1").update());
        activity.setRemainingCapacity(activity.getRemainingCapacity() + 1);
        // 截止后允许取消，但不再递补；候补已转正记录保留为历史，不改成已取消。
        int tag = 1;
        if (activity.getStatus() == 1 && LocalDateTime.now().isBefore(activity.getRegistrationEndTime())) {
            tag = promoteAvailable(activity);
        } else {
            waitListService.lambdaUpdate()
                    .eq(WaitList::getActivityId, activityId).eq(WaitList::getStatus, 0)
                    .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null).update();
        }
        requestService.removeRequestCacheAfterCommit(activityId, userId, tag);
        return Result.ok();
    }


    @Override
    public Result<PageResultVO<RegistrationVO>> pageMyRegistration(Integer pages) {
        if (pages == null || pages < 1) {
            error(ErrorCode.PARAMETER_INVALID, "页码必须大于零");
        }
        Page<Registration> result = lambdaQuery().eq(Registration::getUserId, currentUserId())
                .orderByDesc(Registration::getRegistrationId).page(new Page<>(pages, PAGE_SIZE));
        Set<Long> activityIds = result.getRecords().stream().map(Registration::getActivityId).collect(java.util.stream.Collectors.toSet());
        Map<Long, Activity> activities = activityIds.isEmpty() ? Map.of()
                : activityService.listByIds(activityIds).stream()
                .collect(java.util.stream.Collectors.toMap(Activity::getActivityId, activity -> activity));
        List<RegistrationVO> list = result.getRecords().stream().map(record -> {
            RegistrationVO vo = BeanUtil.copyProperties(record, RegistrationVO.class);
            Activity activity = activities.get(record.getActivityId());
            vo.setActivityName(activity == null ? null : activity.getActivityName());
            vo.setStatusDescription(activity != null && Integer.valueOf(2).equals(activity.getStatus())
                    ? "活动已取消"
                    : switch (record.getStatus()) {
                        case 0 -> "已报名";
                        case 1 -> "已取消报名";
                        default -> "报名已失效";
                    });
            return vo;
        }).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(),
                result.getSize(), result.getPages(), list));
    }

    @Override
    public Result<PageResultVO<RegistrationVO>> pageRegistrationsOfMyActivity(Integer pages, Long activityId) {
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

        Page<Registration> page = new Page<>(pages, PAGE_SIZE);
        Page<Registration> result = lambdaQuery().eq(Registration::getActivityId, activityId).eq(Registration::getStatus, 0).orderByAsc(Registration::getRegistrationId).page(page);
        Set<Long> userIds = result.getRecords().stream().map(Registration::getUserId).collect(java.util.stream.Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of()
                : userService.listByIds(userIds).stream().collect(java.util.stream.Collectors.toMap(User::getUserId, user -> user));
        List<RegistrationVO> list = result.getRecords().stream().map(record -> {
            RegistrationVO vo = BeanUtil.copyProperties(record, RegistrationVO.class);
            User participant = users.get(record.getUserId());
            if (participant != null) {
                vo.setUserName(participant.getUserName());
                vo.setStudentId(participant.getStudentId());
            }
            return vo;
        }).toList();
        return Result.ok(new PageResultVO<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getPages(), list));
    }

    // 报名和取消报名共用递补流程，统一候补顺序和账号状态校验。
    private int promoteAvailable(Activity activity) {
            if (!LocalDateTime.now().isBefore(activity.getRegistrationEndTime())) {
                waitListService.lambdaUpdate()
                        .eq(WaitList::getActivityId, activity.getActivityId()).eq(WaitList::getStatus, 0)
                        .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null).update();
                return 1;
            }
            WaitList head = waitListService.lambdaQuery()
                    .eq(WaitList::getActivityId, activity.getActivityId()).eq(WaitList::getStatus, 0)
                    .orderByAsc(WaitList::getJoinTime, WaitList::getWaitId).last("LIMIT 1").one();
            if (head == null) {
                return 1;
            }
            User candidate = userService.lambdaQuery().eq(User::getUserId, head.getUserId()).one();
            if (candidate == null || !Integer.valueOf(0).equals(candidate.getStatus())) {
                requireSuccess(waitListService.lambdaUpdate()
                        .eq(WaitList::getWaitId, head.getWaitId()).eq(WaitList::getStatus, 0)
                        .set(WaitList::getStatus, 3).set(WaitList::getPromotedTime, null).update());
                requestService.removeRequestCacheAfterCommit(activity.getActivityId(), head.getUserId(), 0);
                return promoteAvailable(activity);
            }

            candidate = userLock.lock(head.getUserId());
            if (candidate == null || !Integer.valueOf(0).equals(candidate.getStatus())) {
                log.error("候补用户id{}状态已变化", head.getUserId());
                error(ErrorCode.INTERNAL_ERROR, "服务器异常");
            }

            activateRegistration(activity.getActivityId(), head.getUserId());
            LocalDateTime promotedTime = LocalDateTime.now();
            if (promotedTime.isBefore(head.getJoinTime())) {
                promotedTime = head.getJoinTime();
            }
            requireSuccess(waitListService.lambdaUpdate()
                    .eq(WaitList::getWaitId, head.getWaitId()).eq(WaitList::getStatus, 0)
                    .set(WaitList::getStatus, 1).set(WaitList::getPromotedTime, promotedTime).update());
            takeSeat(activity);
            return 0;
    }


    private void activateRegistration(Long activityId, Long userId) {
        Registration existing = lambdaQuery()
                .eq(Registration::getActivityId, activityId).eq(Registration::getUserId, userId).one();
        if (existing == null) {
            Registration registration = new Registration();
            registration.setActivityId(activityId);
            registration.setUserId(userId);
            registration.setStatus(0);
            requireSuccess(save(registration));
        } else {
            requireSuccess(lambdaUpdate()
                    .eq(Registration::getRegistrationId, existing.getRegistrationId())
                    .in(Registration::getStatus, 1, 2).set(Registration::getStatus, 0).update());
        }
    }

    // 扣减时再次校验时间和余量，失败由外层事务回滚。
    private void takeSeat(Activity activity) {
        LocalDateTime now = LocalDateTime.now();
        requireSuccess(activityService.lambdaUpdate()
                .eq(Activity::getActivityId, activity.getActivityId())
                .eq(Activity::getStatus, 1).gt(Activity::getRemainingCapacity, 0)
                .le(Activity::getRegistrationStartTime, now)
                .gt(Activity::getRegistrationEndTime, now)
                .setSql("remaining_capacity = remaining_capacity - 1").update());
        activity.setRemainingCapacity(activity.getRemainingCapacity() - 1);
    }

    private static Long currentUserId() {
        UserDTO user = USER_THREAD_LOCAL.get();
        if (user == null || user.getUserId() == null) {
            error(ErrorCode.AUTH_REQUIRED, "用户未登录");
        }
        return user.getUserId();
    }

    private static void requireSuccess(boolean success) {
        if (!success) {
            error(ErrorCode.DATA_STATE_CONFLICT, "数据状态已变化，请重试");
        }
    }

    @Scheduled(fixedDelay = 30L, timeUnit = TimeUnit.SECONDS)
    public void solvePendingMessage() {
        Set<String> range = stringRedisTemplate.opsForZSet().rangeByScore("registration:message:pending", 0L, System.currentTimeMillis());

        for (String requestId : range) {
            try {
                Map<Object, Object> map = stringRedisTemplate.opsForHash().entries("registration:message:" + requestId);
                if (map.isEmpty()) {
                    log.error("请求id{}异常", requestId);
                    stringRedisTemplate.opsForZSet().remove("registration:message:pending" , requestId);
                    continue;
                }
                MessageDTO messageDTO = BeanUtil.fillBeanWithMap(map, new MessageDTO(), false);
                Long result = stringRedisTemplate.execute(REGISTER_ACTIVITY_PENDING_SEND_SCRIPT, List.of("registration:message:" + requestId)
                        , requestId, Long.toString(System.currentTimeMillis()));
                if (result == -1) {
                    log.error("请求id{}脚本参数错误", requestId);
                    continue;
                }
                if (result == -2) {
                    log.error("请求id{}脚本执行时间异常", requestId);
                    continue;
                }
                if(result == -3) {
                    log.error("请求id{}重发消息次数已达上限", requestId);
                    registrationDeadLetterConsumer.updateDatabase(messageDTO);

                    stringRedisTemplate.opsForZSet().remove("registration:message:pending" , requestId);
                    stringRedisTemplate.delete("registration:message:" + requestId);

                    continue;
                }
                rabbitTemplate.convertAndSend("campuspass.direct", "registerActivity",
                        JSONUtil.toJsonStr(messageDTO));
            } catch (Exception e) {
                log.error("请求id{}的消息发送重试出现异常",requestId, e);
            }
        }
    }
}
