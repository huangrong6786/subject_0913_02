package com.evops.algae.service;

import com.evops.algae.dto.BatchCreateRequest;
import com.evops.algae.dto.FeedCreateRequest;
import com.evops.algae.dto.HarvestCreateRequest;
import com.evops.algae.dto.SamplingCreateRequest;
import com.evops.algae.entity.CultureBatch;
import com.evops.algae.entity.FeedEvent;
import com.evops.algae.entity.HarvestRecord;
import com.evops.algae.entity.SamplingEvent;

/**
 * 培养批次闭环的事务边界：每个方法一次事务完成全部跨表写入。
 * 版本冲突以 RetryableConflictException 抛出，由门面重试。
 */
public interface CultureBatchOperations {

    CultureBatch createBatch(BatchCreateRequest request);

    FeedEvent feed(FeedCreateRequest request);

    SamplingEvent sample(SamplingCreateRequest request);

    HarvestRecord harvest(HarvestCreateRequest request);

    CultureBatch transition(String batchNo, String reactorCode, String targetStatus);

    void deleteBatch(String batchNo, String reactorCode);

    void deleteFeed(String feedNo);

    void deleteSampling(String samplingNo);

    void deleteHarvest(String harvestNo);
}
