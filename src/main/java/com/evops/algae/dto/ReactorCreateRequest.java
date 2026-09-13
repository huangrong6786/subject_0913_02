package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class ReactorCreateRequest {
    @NotBlank(message = "培养罐编码不能为空")
    private String reactorCode;
    @NotBlank(message = "培养罐名称不能为空")
    private String reactorName;
    @NotBlank(message = "反应器柜位不能为空")
    private String cabinetPosition;
    @NotNull(message = "罐容量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "罐容量必须大于 0")
    private BigDecimal capacityMl;
}
