package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 培养罐 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_culture_tank")
public class CultureTank extends BizEntity {
    private String tankCode;
    private String tankName;
    private BigDecimal volumeL;
    /** IDLE / OCCUPIED / MAINTENANCE */
    private String status;
}
