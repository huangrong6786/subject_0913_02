package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class TankCreateReq {
    @NotBlank(message = "不能为空")
    private String tankCode;
    @NotBlank(message = "不能为空")
    private String tankName;
    @NotNull(message = "不能为空")
    @DecimalMin(value = "0.001", message = "必须大于 0")
    private BigDecimal volumeL;
    /** 幂等请求号，可选，缺省由服务端生成 */
    private String requestNo;
}
