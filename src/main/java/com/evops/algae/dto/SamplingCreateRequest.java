package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 采样登记。ph/od 为采样时刻强制留存的快照。 */
@Data
public class SamplingCreateRequest {
    @NotBlank(message = "采样事件号不能为空")
    private String samplingNo;
    @NotBlank(message = "培养批次号不能为空")
    private String batchNo;
    @NotBlank(message = "反应器柜位编码不能为空")
    private String reactorCode;
    @NotNull(message = "采样业务日期不能为空")
    private LocalDate bizDate;
    @DecimalMin(value = "0", message = "采样体积不能为负")
    private BigDecimal sampleVolumeMl;
    @NotNull(message = "采样时刻 pH 快照不能为空")
    @DecimalMin(value = "0", message = "pH 不能为负")
    private BigDecimal ph;
    @NotNull(message = "采样时刻光密度快照不能为空")
    @DecimalMin(value = "0", message = "光密度不能为负")
    private BigDecimal od;
    private String remark;
}
