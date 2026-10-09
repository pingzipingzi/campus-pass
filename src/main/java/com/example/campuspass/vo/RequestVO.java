package com.example.campuspass.vo;

import lombok.Data;


@Data
public class RequestVO {
    private String requestId;

    private Long activityId;

    private Long userId;

    private Integer status;

}
