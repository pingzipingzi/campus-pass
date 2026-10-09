package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.WaitList;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.WaitListVO;
import jakarta.validation.constraints.Min;

public interface IWaitListService extends IService<WaitList> {
    Result<PageResultVO<WaitListVO>> pageMyWaitLists(Integer pages);

    Result<Void> cancelWaitList(Long activityId);

    void expireForActivity(Long activityId);

    Result<PageResultVO<WaitListVO>> pageWaitListOfMyActivity(@Min(1) Integer pages, Long activityId);
}
