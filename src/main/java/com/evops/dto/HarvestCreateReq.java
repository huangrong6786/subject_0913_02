package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HarvestCreateReq {
    @NotBlank(message = "不能为空")
    private String harvestNo;
    @NotNull(message = "不能为空")
    private Long batchId;
    @NotNull(message = "不能为空")
    private LocalDate harvestDate;
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0.001", message = "必须大于 0")
    private BigDecimal harvestVolumeL;
    private BigDecimal opticalDensity;
    @DecimalMin(value = "0", message = "超出 pH 量程")
    @DecimalMax(value = "14", message = "超出 pH 量程")
    private BigDecimal ph;
    private String requestNo;
}
