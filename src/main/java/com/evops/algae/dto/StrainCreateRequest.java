package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDate;

@Data
public class StrainCreateRequest {
    @NotBlank(message = "藻种批次号不能为空")
    private String strainNo;
    @NotBlank(message = "藻种名称不能为空")
    private String strainName;
    private String species;
    private String sourceOrg;
    /** 建立批次时即可登记验收（如母批次已随货验收）。 */
    private Boolean accepted;
    private LocalDate acceptedDate;
    private String acceptRemark;
}
