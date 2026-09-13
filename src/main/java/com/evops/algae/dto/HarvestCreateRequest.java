package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采收登记。bizDate 为业务日期 3（采收日期）；ph/od 为采收时刻强制快照。
 */
@Data
public class HarvestCreateRequest {
    @NotBlank(message = "采收批次号不能为空")
    private String harvestNo;
    @NotBlank(message = "培养批次号不能为空")
    private String batchNo;
    @NotBlank(message = "反应器柜位编码不能为空")
    private String reactorCode;
    @NotNull(message = "采收业务日期不能为空")
    private LocalDate bizDate;
    @NotNull(message = "采收量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "采收量必须大于 0")
    private BigDecimal harvestVolumeMl;
    @NotNull(message = "采收时刻 pH 快照不能为空")
    @DecimalMin(value = "0", message = "pH 不能为负")
    private BigDecimal ph;
    @NotNull(message = "采收时刻光密度快照不能为空")
    @DecimalMin(value = "0", message = "光密度不能为负")
    private BigDecimal od;
    /** 采收即落账；落账记录不得删除。默认 true。 */
    private Boolean postToLedger;
}
