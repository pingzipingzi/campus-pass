package com.example.campuspass.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDTO {

    private Long userId;

    private Long activityId;

    private String requestId;

    private LocalDateTime sendTime;

    private Integer cacheStatus; //0是报名成功，1是库存不足候补了

}
