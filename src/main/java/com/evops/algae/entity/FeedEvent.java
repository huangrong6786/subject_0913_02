package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 补液事件。与批次 pH/光密度快照在同一事务内写入。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_feed_event")
public class FeedEvent extends BaseEntity {
    /** 补液事件业务键，全局唯一。 */
    private String feedNo;
    private Long batchId;
    private LocalDate bizDate;
    private String bizTimezone;
    private String medium;
    private BigDecimal feedVolumeMl;
    /** 补液时刻批次 pH 快照。 */
    private BigDecimal phSnapshot;
    /** 补液时刻光密度 OD 快照。 */
    private BigDecimal odSnapshot;
    /** 是否已落账；落账后不得删除。 */
    private Boolean posted;
    private LocalDateTime postedAt;
    private String operatorCode;
    @Version
    private Integer version;
}
