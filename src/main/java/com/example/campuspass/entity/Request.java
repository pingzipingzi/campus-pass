package com.example.campuspass.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@TableName(value = "tb_request")
@Accessors(chain = true)
public class Request {

    @TableId(value = "request_id" ,type = IdType.INPUT)
    private String requestId;

    private Long activityId;

    private Long userId;

    @TableField(value = "is_stock_changed_when_dead_letter")
    private Integer isStockChanged;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    //0 死信补偿失败 1 成功
    private Integer isCompensate;
}
