package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 藻种批次 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_batch")
public class AlgaeBatch extends BizEntity {
    private String batchNo;
    private String species;
    private Long tankId;
    /** 接种日期（业务日期一） */
    private LocalDate inoculationDate;
    private BigDecimal initialOd;
    private BigDecimal latestOd;
    private BigDecimal latestPh;
    private BigDecimal totalFeedL;
    private BigDecimal totalHarvestL;
    /** CULTIVATING / HARVESTING / CLOSED */
    private String status;
}
