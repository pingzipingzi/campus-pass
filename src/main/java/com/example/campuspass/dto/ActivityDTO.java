package com.example.campuspass.dto;

import com.example.campuspass.annotation.ValidActivityTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@ValidActivityTime
public class ActivityDTO {
    @Size(max=100)
    @NotBlank(message = "活动名称不能为空")
    @Schema(name="活动名称",description = "详情略",example = "校园跑")
    private String activityName;

    private String activityDescription;

    @NotNull(message = "活动类型不能为空")
    @Min(0)
    @Max(5)
    private Integer activityType;

    @Size(max=100)
    @NotBlank(message = "活动地点不能为空")
    private String activityLocation;

    @FutureOrPresent(message = "报名开始时间不能早于当前时间")
    @NotNull(message = "活动报名开始时间不能为空")
    private LocalDateTime registrationStartTime;

    @FutureOrPresent(message = "报名截止时间不能早于当前时间")
    @NotNull(message = "活动报名截止时间不能为空")
    private LocalDateTime registrationEndTime;

    @FutureOrPresent(message = "活动开始时间不能早于当前时间")
    @NotNull(message = "活动开始时间不能为空")
    private LocalDateTime startTime;

    @FutureOrPresent(message = "活动结束时间不能早于当前时间")
    @NotNull(message = "活动结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "活动总名额不能为空")
    @Max(2000)
    @Min(1)
    private Integer totalCapacity;

}
