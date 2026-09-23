package com.example.campuspass.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityPageVO {
    private Long activityId;
    private String activityName;
    private String activityLocation;
    private LocalDateTime registrationStartTime;
    private LocalDateTime registrationEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalCapacity;
    private Integer remainingCapacity;
    private Integer status;
}
