package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.WaitListVO;

public interface IWaitListService extends IService<WaitList> {
    Result<PageResultVO<WaitListVO>> pageMyWaitLists(Integer pages);

    Result<Void> cancelWaitList(Long activityId);

    void expireForActivity(Long activityId);
}
