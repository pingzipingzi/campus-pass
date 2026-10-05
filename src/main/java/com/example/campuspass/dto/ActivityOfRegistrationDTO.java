package com.example.campuspass.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivityOfRegistrationDTO {
    private Integer remainingCapacity;
    private Long registrationStartTime;
    private Long registrationEndTime;
}
