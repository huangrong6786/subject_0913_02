package com.evops.controller;

import com.evops.common.ApiResponse;
import com.evops.dto.BatchCreateReq;
import com.evops.dto.FeedCreateReq;
import com.evops.dto.HarvestCreateReq;
import com.evops.dto.TankCreateReq;
import com.evops.entity.AlgaeBatch;
import com.evops.entity.CultureTank;
import com.evops.entity.FeedEvent;
import com.evops.entity.HarvestBatch;
import com.evops.service.BioreactorService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 微藻生物反应器培养与采收闭环 API */
@RestController
@RequestMapping("/api/algae")
public class BioreactorController {

    private final BioreactorService service;

    public BioreactorController(BioreactorService service) {
        this.service = service;
    }

    // 培养罐
    @PostMapping("/tanks")
    public ApiResponse<CultureTank> createTank(@Validated @RequestBody TankCreateReq req) {
        return ApiResponse.ok(service.createTank(req));
    }

    @GetMapping("/tanks")
    public ApiResponse<List<CultureTank>> listTanks() {
        return ApiResponse.ok(service.listTanks());
    }

    // 藻种批次
    @PostMapping("/batches")
    public ApiResponse<AlgaeBatch> createBatch(@Validated @RequestBody BatchCreateReq req) {
        return ApiResponse.ok(service.createBatch(req));
    }

    @GetMapping("/batches")
    public ApiResponse<List<AlgaeBatch>> listBatches(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.listBatches(status));
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<Map<String, Object>> batchDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.batchDetail(id));
    }

    @PostMapping("/batches/{id}/transition")
    public ApiResponse<AlgaeBatch> transitionBatch(@PathVariable Long id, @RequestParam String target) {
        return ApiResponse.ok(service.transitionBatch(id, target));
    }

    // 补液事件
    @PostMapping("/feeds")
    public ApiResponse<FeedEvent> createFeed(@Validated @RequestBody FeedCreateReq req) {
        return ApiResponse.ok(service.createFeed(req));
    }

    @GetMapping("/feeds")
    public ApiResponse<List<FeedEvent>> listFeeds(@RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(service.listFeeds(batchId));
    }

    @DeleteMapping("/feeds/{id}")
    public ApiResponse<Void> deleteFeed(@PathVariable Long id) {
        service.deleteFeed(id);
        return ApiResponse.ok(null);
    }

    // 采收批次
    @PostMapping("/harvests")
    public ApiResponse<HarvestBatch> createHarvest(@Validated @RequestBody HarvestCreateReq req) {
        return ApiResponse.ok(service.createHarvest(req));
    }

    @GetMapping("/harvests")
    public ApiResponse<List<HarvestBatch>> listHarvests(@RequestParam(required = false) Long batchId) {
        return ApiResponse.ok(service.listHarvests(batchId));
    }

    @PostMapping("/harvests/{id}/post")
    public ApiResponse<HarvestBatch> postHarvest(@PathVariable Long id) {
        return ApiResponse.ok(service.postHarvest(id));
    }

    @PostMapping("/harvests/{id}/accept")
    public ApiResponse<HarvestBatch> acceptHarvest(@PathVariable Long id) {
        return ApiResponse.ok(service.acceptHarvest(id));
    }

    @DeleteMapping("/harvests/{id}")
    public ApiResponse<Void> deleteHarvest(@PathVariable Long id) {
        service.deleteHarvest(id);
        return ApiResponse.ok(null);
    }
}
