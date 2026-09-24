package com.example.campuspass.vo;

import lombok.Data;


@Data
public class RegistrationVO {

    private Long registrationId;

    private Long activityId;

    private Long userId;

    private Integer status;

    // 报名状态只表示正式报名关系；候补状态单独返回，未产生对应记录时为 null。
    private Integer waitStatus;

    private String statusDescription;

}
