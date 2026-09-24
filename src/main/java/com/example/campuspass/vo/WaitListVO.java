package com.example.campuspass.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WaitListVO {
    private Long waitId;

    private Long activityId;

    private Long userId;

    private Integer status;

    private LocalDateTime joinTime;

    private LocalDateTime promotedTime;

}
