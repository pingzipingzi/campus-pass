package com.example.campuspass.controller;

import com.example.campuspass.dto.Result;
import com.example.campuspass.service.IWaitListService;
import com.example.campuspass.vo.PageResultVO;
import com.example.campuspass.vo.WaitListVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wait-list")
@Tag(name="候补表管理")
public class WaitListController {

    @Autowired
    private IWaitListService waitListService;


    @GetMapping("/mine")
    public Result<PageResultVO<WaitListVO>> pageMyWaitLists(@RequestParam("pages") Integer pages) {
        return waitListService.pageMyWaitLists(pages);
    }

    @PostMapping("/cancel/{activityId}")
    public Result<Void> cancelWaitList(@PathVariable("activityId") Long activityId) {
        return waitListService.cancelWaitList(activityId);
    }

    @GetMapping("/mine/activity/{activityId}")
    public Result<PageResultVO<WaitListVO>> getMyRegistration(@Min(1) @RequestParam("pages") Integer pages, @PathVariable("activityId") Long activityId) {
        return waitListService.pageWaitListOfMyActivity(pages, activityId);
    }

}
