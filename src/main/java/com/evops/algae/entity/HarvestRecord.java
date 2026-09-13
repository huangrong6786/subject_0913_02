package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 采收批次。与批次 pH/光密度快照在同一事务内写入；落账后不得删除。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_harvest_record")
public class HarvestRecord extends BaseEntity {
    private String harvestNo;
    private Long batchId;
    /** 业务日期 3：采收日期。 */
    private LocalDate bizDate;
    private String bizTimezone;
    private BigDecimal harvestVolumeMl;
    private BigDecimal phSnapshot;
    private BigDecimal odSnapshot;
    private Boolean posted;
    private LocalDateTime postedAt;
    private String operatorCode;
    @Version
    private Integer version;
}
