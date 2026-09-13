package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 补液登记。bizDate 为业务日期；ph/od 为补液时刻强制留存的快照。
 */
@Data
public class FeedCreateRequest {
    @NotBlank(message = "补液事件号不能为空")
    private String feedNo;
    @NotBlank(message = "培养批次号不能为空")
    private String batchNo;
    @NotBlank(message = "反应器柜位编码不能为空")
    private String reactorCode;
    @NotNull(message = "补液业务日期不能为空")
    private LocalDate bizDate;
    private String medium;
    @NotNull(message = "补液量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "补液量必须大于 0")
    private BigDecimal feedVolumeMl;
    @NotNull(message = "补液时刻 pH 快照不能为空")
    @DecimalMin(value = "0", message = "pH 不能为负")
    private BigDecimal ph;
    @NotNull(message = "补液时刻光密度快照不能为空")
    @DecimalMin(value = "0", message = "光密度不能为负")
    private BigDecimal od;
}
