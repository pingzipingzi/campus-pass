package com.example.campuspass.dto;

import lombok.Data;

@Data
public class ActivityOfRegistrationDTO {
    private Integer remainingCapacity;
    private Long registrationStartTime;
    private Long registrationEndTime;
}
