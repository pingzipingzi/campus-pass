package com.example.campuspass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_wait_list")
public class WaitList {

    @TableId(value="wait_id",type= IdType.AUTO)
    private Long waitId;

    private Long activityId;

    private Long userId;

    private Integer status;

    private LocalDateTime joinTime;

    @Schema(description="候补转正时间")
    private LocalDateTime promotedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
