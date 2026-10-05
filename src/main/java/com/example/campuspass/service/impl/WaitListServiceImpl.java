package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.campuspass.entity.Activity;
import com.example.campuspass.service.support.ActivityMutationGuard;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.mapper.WaitListMapper;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.WaitListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;
import java.time.LocalDateTime;

import static com.example.campuspass.exception.BusinessException.error;
import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class WaitListServiceImpl extends ServiceImpl<WaitListMapper, WaitList> implements IWaitListService {

    private static final Integer WAIT_LIST_PAGE_SIZE=10;

    @Autowired
    private ActivityMutationGuard mutationGuard;


    @Override
    public Result<PageResultVO<WaitListVO>> pageMyWaitLists(Integer pages) {
        if (pages == null || pages < 1) {
            error("页码必须大于零");
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result<Void> cancelWaitList(Long activityId) {
        Long userId = USER_THREAD_LOCAL.get().getUserId();
        Activity activity = mutationGuard.lock(activityId);
        WaitList one = lambdaQuery().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId).one();
        if(one==null||one.getStatus()!=0){
            error("您目前未处于候补状态");
        }
        boolean expired = activity.getStatus() == 2
                || !LocalDateTime.now().isBefore(activity.getRegistrationEndTime());
        boolean success = lambdaUpdate().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId)
                .eq(WaitList::getStatus, 0).set(WaitList::getStatus, expired ? 3 : 2)
                .set(WaitList::getPromotedTime, null).update();

        if (!success) {
            error("数据更新失败");
        }
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
}
