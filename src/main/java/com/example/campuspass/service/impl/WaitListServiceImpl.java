package com.example.campuspass.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.User;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.exception.BusinessException;
import com.example.campuspass.mapper.WaitListMapper;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.WaitListVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.example.campuspass.utils.RefreshInterceptor.USER_THREAD_LOCAL;

@Service
public class WaitListServiceImpl extends ServiceImpl<WaitListMapper, WaitList> implements IWaitListService {

    private static final Integer WAIT_LIST_PAGE_SIZE=10;


    @Override
    public Result<PageResultVO<WaitListVO>> pageMyWaitLists(Integer pages) {
        Page<WaitList> page = new Page<>(pages, WAIT_LIST_PAGE_SIZE);
        User user = USER_THREAD_LOCAL.get();
        Page<WaitList> result = lambdaQuery().eq(WaitList::getUserId, user.getUserId()).orderByDesc(WaitList::getWaitId).page(page);
        List<WaitListVO> list =
                result.getRecords().stream()
                        .map(elem -> BeanUtil.copyProperties(elem, WaitListVO.class)).toList();
        return Result.ok(new PageResultVO<>(result.getTotal()
                , result.getCurrent(), result.getSize(), result.getPages(), list));
    }

    @Override
    @Transactional
    public Result<Void> cancelWaitList(Long activityId) {
        Long userId = USER_THREAD_LOCAL.get().getUserId();
        WaitList one = lambdaQuery().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId).one();
        if(one==null||one.getStatus()!=0){
            throw new BusinessException(40000, "您目前未处于候补状态", HttpStatus.BAD_REQUEST);
        }
        boolean success = lambdaUpdate().eq(WaitList::getUserId, userId).eq(WaitList::getActivityId, activityId)
                .eq(WaitList::getStatus, 0).set(WaitList::getStatus, 2).update();

        if (!success) {
            throw new BusinessException(40000, "数据更新失败", HttpStatus.BAD_REQUEST);
        }
        return Result.ok();
    }
}
