package com.evops.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class BatchCreateReq {
    @NotBlank(message = "不能为空")
    private String batchNo;
    @NotBlank(message = "不能为空")
    private String species;
    @NotNull(message = "不能为空")
    private Long tankId;
    @NotNull(message = "不能为空")
    private LocalDate inoculationDate;
    private BigDecimal initialOd;
    private String requestNo;
}
