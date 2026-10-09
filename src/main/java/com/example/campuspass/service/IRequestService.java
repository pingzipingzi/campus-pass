package com.example.campuspass.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.campuspass.dto.Result;
import com.example.campuspass.entity.Request;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RequestVO;

public interface IRequestService extends IService<Request> {
    Result<RequestVO> queryWithRequestId(String requestId);

    Result<PageResultVO<RequestVO>> pageMyRequest(Integer pages);
}
