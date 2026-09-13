package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 培养罐 / 反应器柜位。reactor_code 与 cabinet_position 共同刻画柜位。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_reactor")
public class AlgaeReactor extends BaseEntity {
    /** 培养罐业务键，全局唯一。 */
    private String reactorCode;
    private String reactorName;
    /** 反应器柜位（域内复合键的组成部分，见培养批次）。 */
    private String cabinetPosition;
    private BigDecimal capacityMl;
    /** {@link com.evops.algae.enums.ReactorStatus}。 */
    private String status;
    private String operatorCode;
    @Version
    private Integer version;
}
