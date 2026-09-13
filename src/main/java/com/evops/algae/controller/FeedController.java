package com.evops.algae.controller;

import com.evops.algae.dto.FeedCreateRequest;
import com.evops.algae.entity.FeedEvent;
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
@RequestMapping("/api/algae/feeds")
public class FeedController {

    private final CultureBatchService batchService;

    public FeedController(CultureBatchService batchService) {
        this.batchService = batchService;
    }

    /** 补液登记：与批次 pH/OD 快照、审计在同一事务写入。 */
    @PostMapping
    public ApiResponse<FeedEvent> feed(@Valid @RequestBody FeedCreateRequest request) {
        return ApiResponse.ok(batchService.feed(request));
    }

    /** 删除补液：已落账记录返回 RECORD_LOCKED。 */
    @DeleteMapping("/{feedNo}")
    public ApiResponse<Void> delete(@PathVariable String feedNo) {
        batchService.deleteFeed(feedNo);
        return ApiResponse.ok(null);
    }
}
