package com.evops.algae.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BatchTransitionRequest {
    @NotBlank(message = "目标状态不能为空，取值 RUNNING/COMPLETED/TERMINATED/ACCEPTED")
    private String targetStatus;
}
