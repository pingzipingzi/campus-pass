package com.example.campuspass.annotation.constraint;

import com.example.campuspass.annotation.ValidActivityTime;
import com.example.campuspass.dto.ActivityDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;

public class ActivityTimeValidator implements ConstraintValidator<ValidActivityTime, ActivityDTO> {
    @Override
    public boolean isValid(ActivityDTO dto, ConstraintValidatorContext context) {
        //如果dto是null,交给NotBlank处理
        if(dto==null){
            return true;
        }

        LocalDateTime startTime=dto.getStartTime();
        LocalDateTime endTime=dto.getEndTime();
        LocalDateTime registrationStartTime=dto.getRegistrationStartTime();
        LocalDateTime registrationEndTime=dto.getRegistrationEndTime();

        if (startTime == null || endTime == null || registrationEndTime == null || registrationStartTime == null) {
            return true;
        }
        return registrationStartTime.isBefore(registrationEndTime) && registrationEndTime.isBefore(startTime)
                && startTime.isBefore(endTime);

    }
}
