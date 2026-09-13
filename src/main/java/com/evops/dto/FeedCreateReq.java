package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class FeedCreateReq {
    @NotBlank(message = "不能为空")
    private String feedNo;
    @NotNull(message = "不能为空")
    private Long batchId;
    @NotNull(message = "不能为空")
    private LocalDateTime feedDate;
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0.001", message = "必须大于 0")
    private BigDecimal feedVolumeL;
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0", message = "不能为负")
    private BigDecimal opticalDensity;
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0", message = "超出 pH 量程")
    @DecimalMax(value = "14", message = "超出 pH 量程")
    private BigDecimal ph;
    private String requestNo;
}
