package com.evops.algae.controller;

import com.evops.algae.dto.SamplingCreateRequest;
import com.evops.algae.entity.SamplingEvent;
import com.evops.algae.service.CultureBatchService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/algae/samplings")
public class SamplingController {

    private final CultureBatchService batchService;

    public SamplingController(CultureBatchService batchService) {
        this.batchService = batchService;
    }

    /** 采样登记：pH/光密度快照与批次更新同事务。 */
    @PostMapping
    public ApiResponse<SamplingEvent> sample(@Valid @RequestBody SamplingCreateRequest request) {
        return ApiResponse.ok(batchService.sample(request));
    }

    @DeleteMapping("/{samplingNo}")
    public ApiResponse<Void> delete(@PathVariable String samplingNo) {
        batchService.deleteSampling(samplingNo);
        return ApiResponse.ok(null);
    }
}
