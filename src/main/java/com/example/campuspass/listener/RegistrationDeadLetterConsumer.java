package com.example.campuspass.listener;

import cn.hutool.json.JSONUtil;
import com.example.campuspass.dto.MessageDTO;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.entity.Request;
import static com.example.campuspass.exception.BusinessException.error;

import com.example.campuspass.service.IActivityService;
import com.example.campuspass.service.impl.RequestServiceImpl;
import com.example.campuspass.service.support.ActivityLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class RegistrationDeadLetterConsumer {

    private final ActivityLock activityLock;

    private final RequestServiceImpl requestService;

    private final StringRedisTemplate stringRedisTemplate;

    private final IActivityService activityService;

    private static final DefaultRedisScript<Long> REGISTER_ACTIVITY_DEAD_LETTER ;


    @Autowired
    @Lazy
    private RegistrationDeadLetterConsumer self;

    static {
        REGISTER_ACTIVITY_DEAD_LETTER = new DefaultRedisScript<>();
        REGISTER_ACTIVITY_DEAD_LETTER.setLocation(new ClassPathResource("lua/register_activity_dead_letter.lua"));
        REGISTER_ACTIVITY_DEAD_LETTER.setResultType(Long.class);
    }

    @RabbitListener(bindings=@QueueBinding(
                    value = @Queue(value="campuspass.registration.dlq"),
                    exchange = @Exchange(value="campuspass.registration.dlx"),
                    key="registration.failed"))

    public void consume(String message) {
        MessageDTO messageDTO = JSONUtil.toBean(message, MessageDTO.class);
        if(messageDTO.getActivityId() == null) {
            log.error("消息接收异常");
            return;
        }
        if (!self.updateDatabase(messageDTO)) {
            return;
        }

        self.rebuildCacheDeadLetter(messageDTO.getRequestId());
    }

    @Transactional(isolation= Isolation.READ_COMMITTED)
    public boolean updateDatabase(MessageDTO messageDTO) {

        Long activityId = messageDTO.getActivityId();
        String requestId = messageDTO.getRequestId();
        Long userId = messageDTO.getUserId();
        Integer cacheStatus = messageDTO.getCacheStatus();

        if(userId==null||activityId==null||requestId==null||cacheStatus==null||(cacheStatus!=0&&cacheStatus!=1)) {
            error("消息接收异常");
        }

        Activity activity = activityLock.lock(activityId);

        Request request = requestService.lambdaQuery().eq(Request::getRequestId, requestId).one();

        if (request != null) {
            if (Integer.valueOf(1).equals(request.getStatus())) {
                return false;
            }

            if (Integer.valueOf(2).equals(request.getStatus())) {
                return !Integer.valueOf(1).equals(request.getIsCompensate());
            }
        } else {
            request = new Request();
            request.setRequestId(requestId);
            request.setActivityId(activityId);
            request.setUserId(userId);
            request.setStatus(2);
            request.setIsStockChanged(cacheStatus);
            request.setIsCompensate(0);
            requestService.save(request);
        }
        return true;
    }

    @Transactional(isolation= Isolation.READ_COMMITTED)
    public void rebuildCacheDeadLetter(String requestId) {

        if(requestId==null){
            return;
        }

        Request request = requestService.lambdaQuery().eq(Request::getRequestId, requestId).last("for update").one();

        Long activityId = request.getActivityId();
        Long userId = request.getUserId();
        Integer isStockChanged = request.getIsStockChanged();

        Activity activity = activityService.lambdaQuery().eq(Activity::getActivityId, activityId).one();

        LocalDateTime endTime = activity.getRegistrationEndTime();

        if(!LocalDateTime.now().isBefore(endTime)) {
            requestService.lambdaUpdate().eq(Request::getRequestId, requestId).set(Request::getIsStockChanged, null)
                    .set(Request::getIsCompensate, 1).eq(Request::getStatus,2).update();
            return;
        }

        if(isStockChanged == null) {return ;}

        String activityOfRegistrationKey = "registration:activity:" + activityId;

        String participantsOfActivityKey = "registration:participant:" + activityId + ":" + userId;

        String registrationCompensatedKey = "registration:compensated:" +requestId;

        Long result = stringRedisTemplate.execute(REGISTER_ACTIVITY_DEAD_LETTER,
                List.of(activityOfRegistrationKey, participantsOfActivityKey, registrationCompensatedKey),
                isStockChanged.toString(), requestId, Long.toString(endTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()));

        if (result == -1) {
            error("脚本输入参数有误");
        }

        if(result == -2) {
            log.warn("请求id不匹配 {}", requestId);
            return;
        }

        if(result == -3) {
            log.error("缓存不存在 {}", activityOfRegistrationKey);
            return;
        }

        requestService.lambdaUpdate().eq(Request::getRequestId, requestId).set(Request::getIsStockChanged, null)
                .set(Request::getIsCompensate, 1).eq(Request::getStatus, 2).update();


    }

    @Scheduled(fixedDelay=30,timeUnit = TimeUnit.SECONDS)
    public void checkAndRebuildCacheDeadLetter() {
        List<Request> list = requestService.lambdaQuery().isNotNull(Request::getIsStockChanged).list();
        if(list.isEmpty()) {return ;}
        for (Request request : list) {
            try {
                self.rebuildCacheDeadLetter(request.getRequestId());
            } catch (Exception e) {
                log.error("请求id {}补偿失败", request.getRequestId(),e);
            }
        }
    }
}
