package com.evops.algae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.algae.common.BizErrorCode;
import com.evops.algae.common.BizException;
import com.evops.algae.common.RetryableConflictException;
import com.evops.algae.dto.BatchCreateRequest;
import com.evops.algae.dto.BatchDetailVO;
import com.evops.algae.dto.FeedCreateRequest;
import com.evops.algae.dto.HarvestCreateRequest;
import com.evops.algae.dto.SamplingCreateRequest;
import com.evops.algae.entity.AlgaeReactor;
import com.evops.algae.entity.AlgaeStrain;
import com.evops.algae.entity.CultureBatch;
import com.evops.algae.entity.FeedEvent;
import com.evops.algae.entity.HarvestRecord;
import com.evops.algae.entity.SamplingEvent;
import com.evops.algae.mapper.AlgaeReactorMapper;
import com.evops.algae.mapper.AlgaeStrainMapper;
import com.evops.algae.mapper.CultureBatchMapper;
import com.evops.algae.mapper.FeedEventMapper;
import com.evops.algae.mapper.HarvestRecordMapper;
import com.evops.algae.mapper.SamplingEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 培养批次门面：负责乐观锁冲突重试与只读关联查询。
 * 跨表写入全部下沉到 {@link CultureBatchTxService}，保证一个请求一个事务。
 * 并发补液/采样/采收冲突时重新读取最新版本并重试，满足 5 路并发写入无丢失更新。
 */
@Service
public class CultureBatchService {

    private static final Logger log = LoggerFactory.getLogger(CultureBatchService.class);
    private static final int MAX_ATTEMPTS = 10;

    private final CultureBatchOperations txService;
    private final CultureBatchMapper batchMapper;
    private final AlgaeStrainMapper strainMapper;
    private final AlgaeReactorMapper reactorMapper;
    private final FeedEventMapper feedMapper;
    private final SamplingEventMapper samplingMapper;
    private final HarvestRecordMapper harvestMapper;

    public CultureBatchService(CultureBatchOperations txService,
                               CultureBatchMapper batchMapper,
                               AlgaeStrainMapper strainMapper,
                               AlgaeReactorMapper reactorMapper,
                               FeedEventMapper feedMapper,
                               SamplingEventMapper samplingMapper,
                               HarvestRecordMapper harvestMapper) {
        this.txService = txService;
        this.batchMapper = batchMapper;
        this.strainMapper = strainMapper;
        this.samplingMapper = samplingMapper;
        this.reactorMapper = reactorMapper;
        this.feedMapper = feedMapper;
        this.harvestMapper = harvestMapper;
    }

    public CultureBatch createBatch(BatchCreateRequest request) {
        // 建立批次的冲突来自罐位占用，直接抛出业务错误，无需乐观锁重试。
        return txService.createBatch(request);
    }

    public FeedEvent feed(FeedCreateRequest request) {
        return withRetry("FEED " + request.getFeedNo(), () -> txService.feed(request));
    }

    public SamplingEvent sample(SamplingCreateRequest request) {
        return withRetry("SAMPLE " + request.getSamplingNo(), () -> txService.sample(request));
    }

    public HarvestRecord harvest(HarvestCreateRequest request) {
        return withRetry("HARVEST " + request.getHarvestNo(), () -> txService.harvest(request));
    }

    public CultureBatch transition(String batchNo, String reactorCode, String targetStatus) {
        return withRetry("TRANSITION " + batchNo + "@" + reactorCode,
                () -> txService.transition(batchNo, reactorCode, targetStatus));
    }

    public void deleteBatch(String batchNo, String reactorCode) {
        txService.deleteBatch(batchNo, reactorCode);
    }

    public void deleteFeed(String feedNo) {
        withRetry("DELETE-FEED " + feedNo, () -> {
            txService.deleteFeed(feedNo);
            return null;
        });
    }

    public void deleteSampling(String samplingNo) {
        withRetry("DELETE-SAMPLING " + samplingNo, () -> {
            txService.deleteSampling(samplingNo);
            return null;
        });
    }

    public void deleteHarvest(String harvestNo) {
        withRetry("DELETE-HARVEST " + harvestNo, () -> {
            txService.deleteHarvest(harvestNo);
            return null;
        });
    }

    /** 培养批次聚合视图：关联藻种、培养罐、补液、采样、采收。 */
    public BatchDetailVO detail(String batchNo, String reactorCode) {
        CultureBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<CultureBatch>()
                .eq(CultureBatch::getBatchNo, batchNo)
                .eq(CultureBatch::getReactorCode, reactorCode));
        if (batch == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "培养批次不存在: (" + batchNo + ", " + reactorCode + ")");
        }
        BatchDetailVO vo = new BatchDetailVO();
        vo.setBatch(batch);
        AlgaeStrain strain = strainMapper.selectOne(new LambdaQueryWrapper<AlgaeStrain>()
                .eq(AlgaeStrain::getStrainNo, batch.getStrainNo()));
        if (strain != null) {
            vo.setStrainName(strain.getStrainName());
        }
        AlgaeReactor reactor = reactorMapper.selectOne(new LambdaQueryWrapper<AlgaeReactor>()
                .eq(AlgaeReactor::getReactorCode, batch.getReactorCode()));
        if (reactor != null) {
            vo.setReactorName(reactor.getReactorName());
            vo.setCabinetPosition(reactor.getCabinetPosition());
        }
        vo.setFeeds(feedMapper.selectList(new LambdaQueryWrapper<FeedEvent>()
                .eq(FeedEvent::getBatchId, batch.getId())
                .orderByAsc(FeedEvent::getBizDate).orderByAsc(FeedEvent::getId)));
        vo.setSamplings(samplingMapper.selectList(new LambdaQueryWrapper<SamplingEvent>()
                .eq(SamplingEvent::getBatchId, batch.getId())
                .orderByAsc(SamplingEvent::getBizDate).orderByAsc(SamplingEvent::getId)));
        vo.setHarvests(harvestMapper.selectList(new LambdaQueryWrapper<HarvestRecord>()
                .eq(HarvestRecord::getBatchId, batch.getId())
                .orderByAsc(HarvestRecord::getBizDate).orderByAsc(HarvestRecord::getId)));
        return vo;
    }

    public List<CultureBatch> list(String status, String strainNo) {
        LambdaQueryWrapper<CultureBatch> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(CultureBatch::getStatus, status.trim().toUpperCase());
        }
        if (strainNo != null && !strainNo.trim().isEmpty()) {
            wrapper.eq(CultureBatch::getStrainNo, strainNo.trim());
        }
        wrapper.orderByDesc(CultureBatch::getCreateTime);
        return batchMapper.selectList(wrapper);
    }

    private <T> T withRetry(String action, TransactionalAction<T> actionRunner) {
        RetryableConflictException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return actionRunner.run();
            } catch (RetryableConflictException ex) {
                last = ex;
                long backoff = Math.min(50L * attempt, 200L);
                log.warn("乐观锁冲突，{} 第 {}/{} 次重试: {}",
                        action, attempt, MAX_ATTEMPTS, ex.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BizException(BizErrorCode.CONCURRENT_UPDATE,
                                "并发更新等待被中断");
                    }
                }
            }
        }
        throw new BizException(BizErrorCode.CONCURRENT_UPDATE,
                "并发更新冲突，重试 " + MAX_ATTEMPTS + " 次仍失败: " + last.getMessage());
    }

    @FunctionalInterface
    private interface TransactionalAction<T> {
        T run();
    }
}
