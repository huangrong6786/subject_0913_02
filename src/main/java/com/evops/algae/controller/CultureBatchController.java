package com.evops.algae.controller;

import com.evops.algae.dto.BatchCreateRequest;
import com.evops.algae.dto.BatchDetailVO;
import com.evops.algae.dto.BatchTransitionRequest;
import com.evops.algae.entity.CultureBatch;
import com.evops.algae.service.CultureBatchService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 培养批次：复合业务键 (batchNo, reactorCode)，reactorCode 通过查询参数传递。
 */
@RestController
@RequestMapping("/api/algae/batches")
public class CultureBatchController {

    private final CultureBatchService batchService;

    public CultureBatchController(CultureBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ApiResponse<CultureBatch> create(@Valid @RequestBody BatchCreateRequest request) {
        return ApiResponse.ok(batchService.createBatch(request));
    }

    @PostMapping("/{batchNo}/transition")
    public ApiResponse<CultureBatch> transition(@PathVariable String batchNo,
                                                @RequestParam String reactorCode,
                                                @Valid @RequestBody BatchTransitionRequest request) {
        return ApiResponse.ok(
                batchService.transition(batchNo, reactorCode, request.getTargetStatus()));
    }

    @DeleteMapping("/{batchNo}")
    public ApiResponse<Void> delete(@PathVariable String batchNo,
                                    @RequestParam String reactorCode) {
        batchService.deleteBatch(batchNo, reactorCode);
        return ApiResponse.ok(null);
    }

    /** 关联查询：批次 + 藻种/罐位名称 + 补液/采样/采收事件。 */
    @GetMapping("/{batchNo}/detail")
    public ApiResponse<BatchDetailVO> detail(@PathVariable String batchNo,
                                             @RequestParam String reactorCode) {
        return ApiResponse.ok(batchService.detail(batchNo, reactorCode));
    }

    @GetMapping
    public ApiResponse<List<CultureBatch>> list(@RequestParam(required = false) String status,
                                                @RequestParam(required = false) String strainNo) {
        return ApiResponse.ok(batchService.list(status, strainNo));
    }
}
