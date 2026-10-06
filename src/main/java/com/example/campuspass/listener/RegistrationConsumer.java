package com.example.campuspass.listener;

import cn.hutool.json.JSONUtil;
import com.example.campuspass.dto.MessageDTO;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Request;
import com.example.campuspass.entity.User;
import com.example.campuspass.service.impl.RegistrationServiceImpl;
import com.example.campuspass.service.impl.RequestServiceImpl;
import com.example.campuspass.service.impl.UserServiceImpl;
import com.example.campuspass.service.support.ActivityMutationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.example.campuspass.exception.BusinessException.error;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationConsumer {


    private final RegistrationServiceImpl registrationService;

    private final RequestServiceImpl requestService;

    private final UserServiceImpl userService;

    private final ActivityMutationGuard mutationGuard;

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> REGISTER_ACTIVITY_PENDING_SCRIPT;

    static {
        REGISTER_ACTIVITY_PENDING_SCRIPT = new DefaultRedisScript<>();
        REGISTER_ACTIVITY_PENDING_SCRIPT.setResultType(Long.class);
        REGISTER_ACTIVITY_PENDING_SCRIPT.setLocation(new ClassPathResource("lua/register_activity_pending_check.lua"));
    }

    @RabbitListener(bindings = @QueueBinding(value = @Queue(
            value = "campuspass.queue1",
            arguments = {@Argument(
                    name = "x-dead-letter-exchange",
                    value = "campuspass.registration.dlx"
            ),
                    @Argument(
                            name = "x-dead-letter-routing-key",
                            value = "registration.failed"
                    )}
    ),
            exchange = @Exchange(value = "campuspass.direct"),
            key = {"registerActivity"}))
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void registerActivity(String message) {

        MessageDTO messageDTO = JSONUtil.toBean(message, MessageDTO.class);
        if (messageDTO == null) {
            error("消息接收异常");
        }

        Long activityId = messageDTO.getActivityId();
        Long userId = messageDTO.getUserId();
        String requestId = messageDTO.getRequestId();
        Long sendTimeStamp = messageDTO.getSendTime();
        Integer cacheStatus = messageDTO.getCacheStatus();

        if (userId == null || activityId == null || requestId == null || sendTimeStamp == null ||
                cacheStatus == null || (cacheStatus != 0 && cacheStatus != 1)) {
            error("消息接收异常");
        }

        LocalDateTime sendTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(sendTimeStamp), ZoneId.of("Asia/Shanghai"));

        Long result = stringRedisTemplate.execute(REGISTER_ACTIVITY_PENDING_SCRIPT,
                List.of("registration:message:" + requestId), requestId);
        if(result == -1) {log.error("脚本参数异常");
            error("服务器异常");}

        if (result == -2) {
            log.warn("请求id{}已发送成功", requestId);
            Request request = requestService.lambdaQuery().eq(Request::getRequestId, requestId).one();
            if(request!=null&&request.getStatus()!=0) {
                return;}
        }

        User user = userService.lambdaQuery().eq(User::getUserId, userId).eq(User::getStatus, 0).one();
        if (user == null) {
            error("用户异常");
        }
        Activity activity = mutationGuard.lock(activityId);

        if (activity == null || activity.getStatus() != 1) {
            error("活动异常");
        }

        if (sendTime.isAfter(activity.getRegistrationEndTime())
                || sendTime.isBefore(activity.getRegistrationStartTime())) {
            error("不在报名时间内");
        }

        Request request = requestService.lambdaQuery().eq(Request::getRequestId, requestId).one();
        if (request != null) {
            log.warn("用户id{},活动id{}重复消费", userId, activityId);
            return;
        }

        request = new Request();
        request.setActivityId(activityId);
        request.setUserId(userId);
        request.setRequestId(requestId);
        request.setStatus(0);
        boolean saveRequest = requestService.save(request);

        if (!saveRequest) {
            error("服务器异常，请重试");
        }

        registrationService.processMessage(activity, userId, requestId,cacheStatus);

    }

}
