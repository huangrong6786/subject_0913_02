package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 培养批次。复合业务键 = (batch_no, reactor_code)：同一培养批次号在同一反应器柜位上唯一。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_culture_batch")
public class CultureBatch extends BaseEntity {
    private String batchNo;
    private String reactorCode;
    private String strainNo;
    /** {@link com.evops.algae.enums.BatchStatus}。 */
    private String status;
    private BigDecimal initialVolumeMl;
    private BigDecimal currentVolumeMl;
    /** 业务日期 1：接种日期。 */
    private LocalDate inoculationDate;
    /** 接种业务时区。 */
    private String inoculationTimezone;
    private BigDecimal latestPh;
    private BigDecimal latestOd;
    /** 业务日期 2：最近一次补液/采样/采收的测量日期。 */
    private LocalDate latestMeasureDate;
    private String operatorCode;
    @Version
    private Integer version;
}
