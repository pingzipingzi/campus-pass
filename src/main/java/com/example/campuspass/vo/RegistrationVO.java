package com.example.campuspass.vo;

import lombok.Data;


@Data
public class RegistrationVO {

    private Long registrationId;

    private Long activityId;

    private Long userId;

    private Integer status;

    private String statusDescription;

}
