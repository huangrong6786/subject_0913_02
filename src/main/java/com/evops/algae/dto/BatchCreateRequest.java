package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 建立培养批次。复合业务键 = (batchNo, reactorCode)；
 * inoculationDate 为业务日期 1（接种日期）。
 */
@Data
public class BatchCreateRequest {
    @NotBlank(message = "培养批次号不能为空")
    private String batchNo;
    @NotBlank(message = "培养罐编码不能为空")
    private String reactorCode;
    @NotBlank(message = "藻种批次号不能为空")
    private String strainNo;
    @NotNull(message = "初始体积不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "初始体积必须大于 0")
    private BigDecimal initialVolumeMl;
    @NotNull(message = "接种日期不能为空")
    private LocalDate inoculationDate;
    /** 初始光密度（接种时刻快照）。 */
    @NotNull(message = "初始光密度不能为空")
    @DecimalMin(value = "0", message = "光密度不能为负")
    private BigDecimal initialOd;
    /** 初始 pH（接种时刻快照）。 */
    @NotNull(message = "初始 pH 不能为空")
    @DecimalMin(value = "0", message = "pH 不能为负")
    private BigDecimal initialPh;
}
