package com.example.campuspass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@TableName("tb_activity")
@Accessors(chain = true)
public class Activity {
    @TableId(value="activity_id" ,type=IdType.AUTO)
    private Long activityId;
    private String activityName;
    private String activityDescription;
    private Integer activityType;
    private String activityLocation;
    private LocalDateTime registrationStartTime;
    private LocalDateTime registrationEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalCapacity;
    private Integer remainingCapacity;
    private Integer status;
    private Long creatorId;
    private Integer registrationCacheStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
