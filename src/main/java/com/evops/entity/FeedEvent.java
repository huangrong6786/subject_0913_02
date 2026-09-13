package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 补液事件：创建即落账，记录反应器光密度与 pH */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_feed_event")
public class FeedEvent extends BizEntity {
    private String feedNo;
    private Long batchId;
    private Long tankId;
    /** 补液时间（业务日期二） */
    private LocalDateTime feedDate;
    private BigDecimal feedVolumeL;
    private BigDecimal opticalDensity;
    private BigDecimal ph;
}
