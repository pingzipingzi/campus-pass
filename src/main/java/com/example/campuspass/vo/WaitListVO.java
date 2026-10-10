package com.example.campuspass.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WaitListVO {
    private Long waitId;

    private Long activityId;

    private String activityName;

    private Long userId;

    private String userName;

    private String studentId;

    private Integer status;

    private String statusDescription;

    private LocalDateTime joinTime;

    private LocalDateTime promotedTime;

}
