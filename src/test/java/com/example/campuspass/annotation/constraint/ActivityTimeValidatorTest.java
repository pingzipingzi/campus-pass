package com.example.campuspass.annotation.constraint;

import com.example.campuspass.dto.ActivityDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTimeValidatorTest {

    private final ActivityTimeValidator validator = new ActivityTimeValidator();

    @Test
    void acceptsStrictlyOrderedTimes() {
        LocalDateTime base = LocalDateTime.of(2030, 1, 1, 8, 0);
        ActivityDTO dto = activityWithTimes(
                base,
                base.plusHours(1),
                base.plusHours(2),
                base.plusHours(3));

        assertTrue(validator.isValid(dto, null));
    }

    @Test
    void rejectsEqualAdjacentTimes() {
        LocalDateTime base = LocalDateTime.of(2030, 1, 1, 8, 0);
        ActivityDTO dto = activityWithTimes(
                base,
                base.plusHours(1),
                base.plusHours(1),
                base.plusHours(3));

        assertFalse(validator.isValid(dto, null));
    }

    @Test
    void rejectsReversedTimes() {
        LocalDateTime base = LocalDateTime.of(2030, 1, 1, 8, 0);
        ActivityDTO dto = activityWithTimes(
                base.plusHours(1),
                base,
                base.plusHours(2),
                base.plusHours(3));

        assertFalse(validator.isValid(dto, null));
    }

    @Test
    void leavesMissingFieldsToFieldLevelValidation() {
        assertTrue(validator.isValid(new ActivityDTO(), null));
        assertTrue(validator.isValid(null, null));
    }

    private ActivityDTO activityWithTimes(
            LocalDateTime registrationStart,
            LocalDateTime registrationEnd,
            LocalDateTime activityStart,
            LocalDateTime activityEnd) {
        ActivityDTO dto = new ActivityDTO();
        dto.setRegistrationStartTime(registrationStart);
        dto.setRegistrationEndTime(registrationEnd);
        dto.setStartTime(activityStart);
        dto.setEndTime(activityEnd);
        return dto;
    }
}
