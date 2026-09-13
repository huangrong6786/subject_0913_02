package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 采样事件。与批次 pH/光密度快照在同一事务内写入。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_sampling_event")
public class SamplingEvent extends BaseEntity {
    private String samplingNo;
    private Long batchId;
    private LocalDate bizDate;
    private String bizTimezone;
    private BigDecimal sampleVolumeMl;
    private BigDecimal phSnapshot;
    private BigDecimal odSnapshot;
    private String remark;
    private String operatorCode;
    @Version
    private Integer version;
}
