package com.example.campuspass.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDTO {

    private Long userId;

    private Long activityId;

    private String requestId;

    private Long sendTime;

    private Integer cacheStatus; //0是报名成功，1是库存不足候补了

    private Integer isPending;//消息是否被队列接收成功，1表示还在待处理中

    private Integer failureTimes;//消息被消息队列接收失败的次数


}
