package com.evops.algae.controller;

import com.evops.algae.dto.HarvestCreateRequest;
import com.evops.algae.entity.HarvestRecord;
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
@RequestMapping("/api/algae/harvests")
public class HarvestController {

    private final CultureBatchService batchService;

    public HarvestController(CultureBatchService batchService) {
        this.batchService = batchService;
    }

    /** 采收登记：采收量扣减、pH/光密度快照、落账标记与审计同事务。 */
    @PostMapping
    public ApiResponse<HarvestRecord> harvest(@Valid @RequestBody HarvestCreateRequest request) {
        return ApiResponse.ok(batchService.harvest(request));
    }

    /** 删除采收：已落账采收记录返回 RECORD_LOCKED。 */
    @DeleteMapping("/{harvestNo}")
    public ApiResponse<Void> delete(@PathVariable String harvestNo) {
        batchService.deleteHarvest(harvestNo);
        return ApiResponse.ok(null);
    }
}
