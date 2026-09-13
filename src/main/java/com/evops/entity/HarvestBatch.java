package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 采收批次：DRAFT -> POSTED -> ACCEPTED，落账/验收后不可直接删除 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_harvest_batch")
public class HarvestBatch extends BizEntity {
    private String harvestNo;
    private Long batchId;
    private Long tankId;
    /** 采收日期（业务日期三） */
    private LocalDate harvestDate;
    private BigDecimal harvestVolumeL;
    private BigDecimal opticalDensity;
    private BigDecimal ph;
    /** DRAFT / POSTED / ACCEPTED */
    private String status;
    private LocalDateTime postedTime;
    private LocalDateTime acceptedTime;
}
