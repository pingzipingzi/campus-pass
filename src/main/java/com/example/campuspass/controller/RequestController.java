package com.example.campuspass.controller;

import com.example.campuspass.dto.Result;
import com.example.campuspass.service.IRequestService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.RequestVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/request")
@Tag(name = "请求表管理")
public class RequestController {

    @Autowired
    private IRequestService requestService;

    @GetMapping("/{requestId}")
    public Result<RequestVO> queryWithRequestId(@PathVariable("requestId")String requestId){
        return requestService.queryWithRequestId(requestId);
    }

    @GetMapping("/mine")
    public Result<PageResultVO<RequestVO>> pageMyRequest(@Min(1) @RequestParam("pages") Integer pages) {
        return requestService.pageMyRequest(pages);
    }
}
