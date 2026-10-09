package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.dto.UserDTO;
import com.example.campuspass.entity.Request;
import com.example.campuspass.mapper.RequestMapper;
import com.example.campuspass.service.IRequestService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
@Slf4j
public class RequestServiceImpl extends ServiceImpl<RequestMapper, Request> implements IRequestService {

    private static final int PAGE_SIZE = 10;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> REMOVE_REQUEST_CACHE_SCRIPT;

    static {
        REMOVE_REQUEST_CACHE_SCRIPT = new DefaultRedisScript<>();
        REMOVE_REQUEST_CACHE_SCRIPT.setResultType(Long.class);
        REMOVE_REQUEST_CACHE_SCRIPT.setLocation(new ClassPathResource("lua/remove_request_cache.lua"));
    }


    @Override
    public Result<RequestVO> queryWithRequestId(String requestId) {

        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if(userDTO == null){
            error("用户未登录");}
        Long userId = userDTO.getUserId();
        Request request = lambdaQuery().eq(Request::getRequestId, requestId).eq(Request::getUserId, userId).one();
        if(request==null){
            error("请求不存在或仍在处理中");
        }
        RequestVO requestVO = new RequestVO();
        BeanUtils.copyProperties(request,requestVO);
        return Result.ok(requestVO);
    }

    @Override
    public Result<PageResultVO<RequestVO>> pageMyRequest(Integer pages) {
        if(pages==null||pages==0){
            error("页码必须大于0");
        }
        UserDTO userDTO = USER_THREAD_LOCAL.get();
        if(userDTO==null||userDTO.getUserId()==null){
            error("用户未登录");
        }
        Page<Request> page = lambdaQuery().eq(Request::getUserId, userDTO.getUserId()).orderByDesc(Request::getCreateTime).page(new Page<Request>(pages, PAGE_SIZE));
        List<RequestVO> list = page.getRecords().stream().map(record -> BeanUtil.copyProperties(record, RequestVO.class)).toList();

        return Result.ok(new PageResultVO<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getPages(), list));
    }

    //sum 表示引起的库存增加
    public void removeRequestCacheAfterCommit(Long activityId,Long userId,int tag) {

        String participantsOfActivityKey = "registration:participant:" + activityId + ":" + userId;

        String registrationActivityKey = "registration:activity:" + activityId;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    Long result = stringRedisTemplate.execute(REMOVE_REQUEST_CACHE_SCRIPT,
                            List.of(participantsOfActivityKey, registrationActivityKey), Integer.toString(tag));
                    if (result == -1) {
                        log.error("用户id{},活动id{} 脚本参数错误", userId, activityId);
                    }else if(result == -2){
                        log.error("用活动id{} 缓存key异常", activityId);
                    }
                } catch (Exception e) {
                    log.error("用户id{},活动id{} 参与缓存删除失败", userId, activityId, e);
                }
            }
        });

    }
}
