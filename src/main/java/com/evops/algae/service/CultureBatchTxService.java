package com.evops.algae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.algae.audit.AuditService;
import com.evops.algae.common.BizErrorCode;
import com.evops.algae.common.BizException;
import com.evops.algae.common.RetryableConflictException;
import com.evops.algae.context.BizRequestContext;
import com.evops.algae.dto.FeedCreateRequest;
import com.evops.algae.dto.HarvestCreateRequest;
import com.evops.algae.dto.SamplingCreateRequest;
import com.evops.algae.entity.AlgaeReactor;
import com.evops.algae.entity.AlgaeStrain;
import com.evops.algae.entity.CultureBatch;
import com.evops.algae.entity.FeedEvent;
import com.evops.algae.entity.HarvestRecord;
import com.evops.algae.entity.SamplingEvent;
import com.evops.algae.enums.BatchStatus;
import com.evops.algae.enums.ReactorStatus;
import com.evops.algae.mapper.AlgaeReactorMapper;
import com.evops.algae.mapper.AlgaeStrainMapper;
import com.evops.algae.mapper.CultureBatchMapper;
import com.evops.algae.mapper.FeedEventMapper;
import com.evops.algae.mapper.HarvestRecordMapper;
import com.evops.algae.mapper.SamplingEventMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 培养批次闭环的事务内操作。每个方法都在同一个事务内完成跨表写入：
 * 业务事件 + 批次快照推进 + 审计日志，任一失败整体回滚。
 * 版本冲突抛出 {@link RetryableConflictException} 交由外层重试。
 */
@Service
public class CultureBatchTxService implements CultureBatchOperations {

    private final CultureBatchMapper batchMapper;
    private final AlgaeStrainMapper strainMapper;
    private final AlgaeReactorMapper reactorMapper;
    private final FeedEventMapper feedMapper;
    private final SamplingEventMapper samplingMapper;
    private final HarvestRecordMapper harvestMapper;
    private final AuditService auditService;

    public CultureBatchTxService(CultureBatchMapper batchMapper,
                                 AlgaeStrainMapper strainMapper,
                                 AlgaeReactorMapper reactorMapper,
                                 FeedEventMapper feedMapper,
                                 SamplingEventMapper samplingMapper,
                                 HarvestRecordMapper harvestMapper,
                                 AuditService auditService) {
        this.batchMapper = batchMapper;
        this.strainMapper = strainMapper;
        this.reactorMapper = reactorMapper;
        this.feedMapper = feedMapper;
        this.samplingMapper = samplingMapper;
        this.harvestMapper = harvestMapper;
        this.auditService = auditService;
    }

    /** 建立批次 + 占用罐位，同一事务。复合业务键 (batchNo, reactorCode) 唯一。 */
    @Override
    @Transactional
    public CultureBatch createBatch(com.evops.algae.dto.BatchCreateRequest request) {
        String operator = BizRequestContext.get().getOperatorCode();
        String timezone = BizRequestContext.get().getBizTimezone();

        AlgaeStrain strain = strainMapper.selectOne(new LambdaQueryWrapper<AlgaeStrain>()
                .eq(AlgaeStrain::getStrainNo, request.getStrainNo()));
        if (strain == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "藻种批次不存在: " + request.getStrainNo());
        }
        AlgaeReactor reactor = reactorMapper.selectOne(new LambdaQueryWrapper<AlgaeReactor>()
                .eq(AlgaeReactor::getReactorCode, request.getReactorCode()));
        if (reactor == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "培养罐不存在: " + request.getReactorCode());
        }
        // 复合业务键重复检查先于罐位状态：同键重复建立明确报业务键冲突。
        boolean duplicated = batchMapper.exists(new LambdaQueryWrapper<CultureBatch>()
                .eq(CultureBatch::getBatchNo, request.getBatchNo())
                .eq(CultureBatch::getReactorCode, request.getReactorCode()));
        if (duplicated) {
            throw new BizException(BizErrorCode.BIZ_KEY_DUPLICATED,
                    "培养批次复合业务键已存在: (" + request.getBatchNo()
                            + ", " + request.getReactorCode() + ")");
        }
        if (!ReactorStatus.AVAILABLE.name().equals(reactor.getStatus())) {
            throw new BizException(BizErrorCode.ILLEGAL_STATUS,
                    "培养罐当前不可用（" + reactor.getStatus() + "）: " + reactor.getReactorCode());
        }
        if (request.getInitialVolumeMl().compareTo(reactor.getCapacityMl()) > 0) {
            throw new BizException(BizErrorCode.VOLUME_EXCEEDED, "初始体积超过培养罐容量");
        }

        CultureBatch batch = new CultureBatch();
        batch.setBatchNo(request.getBatchNo());
        batch.setReactorCode(request.getReactorCode());
        batch.setStrainNo(request.getStrainNo());
        batch.setStatus(BatchStatus.CREATED.name());
        batch.setInitialVolumeMl(request.getInitialVolumeMl());
        batch.setCurrentVolumeMl(request.getInitialVolumeMl());
        batch.setInoculationDate(request.getInoculationDate());
        batch.setInoculationTimezone(timezone);
        batch.setLatestPh(request.getInitialPh());
        batch.setLatestOd(request.getInitialOd());
        batch.setLatestMeasureDate(request.getInoculationDate());
        batch.setOperatorCode(operator);
        batch.setVersion(0);
        batchMapper.insert(batch);

        reactor.setStatus(ReactorStatus.OCCUPIED.name());
        reactor.setOperatorCode(operator);
        if (reactorMapper.updateById(reactor) == 0) {
            // 并发建立：罐位刚被别的批次抢走，事务回滚。
            throw new BizException(BizErrorCode.ILLEGAL_STATUS,
                    "培养罐已被其他批次占用: " + reactor.getReactorCode());
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("batchNo", batch.getBatchNo());
        snapshot.put("reactorCode", batch.getReactorCode());
        snapshot.put("strainNo", batch.getStrainNo());
        snapshot.put("status", batch.getStatus());
        snapshot.put("initialVolumeMl", batch.getInitialVolumeMl());
        snapshot.put("initialPh", batch.getLatestPh());
        snapshot.put("initialOd", batch.getLatestOd());
        snapshot.put("inoculationDate", batch.getInoculationDate());
        snapshot.put("batchVersion", batch.getVersion());
        snapshot.put("reactorVersionAfter", reactor.getVersion());
        auditService.record("CULTURE_BATCH", "CREATE",
                "batchNo=" + batch.getBatchNo() + ",reactorCode=" + batch.getReactorCode(),
                batch.getInoculationDate(), snapshot);
        return batch;
    }

    /** 补液：事件留痕 + 批次体积/pH/OD 快照推进，同一事务。 */
    @Override
    @Transactional
    public FeedEvent feed(FeedCreateRequest request) {
        String operator = BizRequestContext.get().getOperatorCode();
        String timezone = BizRequestContext.get().getBizTimezone();
        CultureBatch batch = loadRunningBatch(request.getBatchNo(), request.getReactorCode());
        AlgaeReactor reactor = loadReactor(batch.getReactorCode());
        checkBizDate(batch, request.getBizDate());
        if (feedMapper.exists(new LambdaQueryWrapper<FeedEvent>()
                .eq(FeedEvent::getFeedNo, request.getFeedNo()))) {
            throw new BizException(BizErrorCode.BIZ_KEY_DUPLICATED,
                    "补液事件号已存在: " + request.getFeedNo());
        }
        checkPhRange(request.getPh());

        int versionBefore = batch.getVersion();
        // 先写事件，守卫更新失败则整体回滚，不会留下无主事件。
        FeedEvent event = new FeedEvent();
        event.setFeedNo(request.getFeedNo());
        event.setBatchId(batch.getId());
        event.setBizDate(request.getBizDate());
        event.setBizTimezone(timezone);
        event.setMedium(request.getMedium());
        event.setFeedVolumeMl(request.getFeedVolumeMl());
        event.setPhSnapshot(request.getPh());
        event.setOdSnapshot(request.getOd());
        event.setPosted(false);
        event.setOperatorCode(operator);
        event.setVersion(0);
        feedMapper.insert(event);

        int rows = batchMapper.applyVolumeChange(batch.getId(), versionBefore,
                request.getFeedVolumeMl(), reactor.getCapacityMl(),
                request.getPh(), request.getOd(), request.getBizDate(), operator);
        if (rows == 0) {
            diagnoseGuardFailure(batch, versionBefore, request.getFeedVolumeMl(),
                    reactor.getCapacityMl(), "补液后体积超出培养罐容量");
        }

        auditService.record("FEED", "FEED",
                "feedNo=" + event.getFeedNo() + ",batchNo=" + batch.getBatchNo()
                        + ",reactorCode=" + batch.getReactorCode(),
                request.getBizDate(),
                volumeSnapshot(batch, event.getFeedNo(), "FEED", request.getBizDate(),
                        versionBefore, request.getFeedVolumeMl(),
                        request.getPh(), request.getOd()));
        return event;
    }

    /** 采样：pH/OD 快照必录；有采样体积时同步扣减批次体积。 */
    @Override
    @Transactional
    public SamplingEvent sample(SamplingCreateRequest request) {
        String operator = BizRequestContext.get().getOperatorCode();
        String timezone = BizRequestContext.get().getBizTimezone();
        CultureBatch batch = loadRunningBatch(request.getBatchNo(), request.getReactorCode());
        AlgaeReactor reactor = loadReactor(batch.getReactorCode());
        checkBizDate(batch, request.getBizDate());
        if (samplingMapper.exists(new LambdaQueryWrapper<SamplingEvent>()
                .eq(SamplingEvent::getSamplingNo, request.getSamplingNo()))) {
            throw new BizException(BizErrorCode.BIZ_KEY_DUPLICATED,
                    "采样事件号已存在: " + request.getSamplingNo());
        }
        checkPhRange(request.getPh());

        BigDecimal delta = request.getSampleVolumeMl() == null
                ? BigDecimal.ZERO : request.getSampleVolumeMl().negate();
        int versionBefore = batch.getVersion();

        SamplingEvent event = new SamplingEvent();
        event.setSamplingNo(request.getSamplingNo());
        event.setBatchId(batch.getId());
        event.setBizDate(request.getBizDate());
        event.setBizTimezone(timezone);
        event.setSampleVolumeMl(request.getSampleVolumeMl());
        event.setPhSnapshot(request.getPh());
        event.setOdSnapshot(request.getOd());
        event.setRemark(request.getRemark());
        event.setOperatorCode(operator);
        event.setVersion(0);
        samplingMapper.insert(event);

        int rows = batchMapper.applyVolumeChange(batch.getId(), versionBefore,
                delta, reactor.getCapacityMl(),
                request.getPh(), request.getOd(), request.getBizDate(), operator);
        if (rows == 0) {
            diagnoseGuardFailure(batch, versionBefore, delta, reactor.getCapacityMl(),
                    "采样体积超过批次当前体积");
        }

        auditService.record("SAMPLING", "SAMPLE",
                "samplingNo=" + event.getSamplingNo() + ",batchNo=" + batch.getBatchNo()
                        + ",reactorCode=" + batch.getReactorCode(),
                request.getBizDate(),
                volumeSnapshot(batch, event.getSamplingNo(), "SAMPLING", request.getBizDate(),
                        versionBefore, delta, request.getPh(), request.getOd()));
        return event;
    }

    /** 采收：采收量扣减 + pH/OD 快照；默认即落账，落账后不可删除。 */
    @Override
    @Transactional
    public HarvestRecord harvest(HarvestCreateRequest request) {
        String operator = BizRequestContext.get().getOperatorCode();
        String timezone = BizRequestContext.get().getBizTimezone();
        CultureBatch batch = loadRunningBatch(request.getBatchNo(), request.getReactorCode());
        AlgaeReactor reactor = loadReactor(batch.getReactorCode());
        checkBizDate(batch, request.getBizDate());
        if (harvestMapper.exists(new LambdaQueryWrapper<HarvestRecord>()
                .eq(HarvestRecord::getHarvestNo, request.getHarvestNo()))) {
            throw new BizException(BizErrorCode.BIZ_KEY_DUPLICATED,
                    "采收批次号已存在: " + request.getHarvestNo());
        }
        checkPhRange(request.getPh());

        BigDecimal delta = request.getHarvestVolumeMl().negate();
        int versionBefore = batch.getVersion();
        boolean postToLedger = request.getPostToLedger() == null || request.getPostToLedger();

        HarvestRecord record = new HarvestRecord();
        record.setHarvestNo(request.getHarvestNo());
        record.setBatchId(batch.getId());
        record.setBizDate(request.getBizDate());
        record.setBizTimezone(timezone);
        record.setHarvestVolumeMl(request.getHarvestVolumeMl());
        record.setPhSnapshot(request.getPh());
        record.setOdSnapshot(request.getOd());
        record.setPosted(postToLedger);
        record.setPostedAt(postToLedger ? LocalDateTime.now() : null);
        record.setOperatorCode(operator);
        record.setVersion(0);
        harvestMapper.insert(record);

        int rows = batchMapper.applyVolumeChange(batch.getId(), versionBefore,
                delta, reactor.getCapacityMl(),
                request.getPh(), request.getOd(), request.getBizDate(), operator);
        if (rows == 0) {
            diagnoseGuardFailure(batch, versionBefore, delta, reactor.getCapacityMl(),
                    "采收量超过批次当前体积");
        }

        auditService.record("HARVEST", "HARVEST",
                "harvestNo=" + record.getHarvestNo() + ",batchNo=" + batch.getBatchNo()
                        + ",reactorCode=" + batch.getReactorCode(),
                request.getBizDate(),
                volumeSnapshot(batch, record.getHarvestNo(), "HARVEST", request.getBizDate(),
                        versionBefore, delta, request.getPh(), request.getOd()));
        return record;
    }

    /** 批次状态流转；终态释放罐位。 */
    @Override
    @Transactional
    public CultureBatch transition(String batchNo, String reactorCode, String targetStatusRaw) {
        String operator = BizRequestContext.get().getOperatorCode();
        BatchStatus target;
        try {
            target = BatchStatus.valueOf(targetStatusRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "未知目标状态: " + targetStatusRaw);
        }
        CultureBatch batch = loadBatch(batchNo, reactorCode);
        BatchStatus current = BatchStatus.valueOf(batch.getStatus());
        boolean freeReactor = false;
        switch (target) {
            case RUNNING:
                if (current != BatchStatus.CREATED) {
                    throw illegalTransition(current, target);
                }
                break;
            case COMPLETED:
                if (current != BatchStatus.RUNNING) {
                    throw illegalTransition(current, target);
                }
                freeReactor = true;
                break;
            case TERMINATED:
                if (current != BatchStatus.CREATED && current != BatchStatus.RUNNING) {
                    throw illegalTransition(current, target);
                }
                freeReactor = true;
                break;
            case ACCEPTED:
                if (current != BatchStatus.COMPLETED) {
                    throw illegalTransition(current, target);
                }
                break;
            default:
                throw new BizException(BizErrorCode.BAD_REQUEST, "不支持的目标状态: " + target);
        }

        int versionBefore = batch.getVersion();
        int rows = batchMapper.updateStatus(batch.getId(), versionBefore,
                target.name(), operator);
        if (rows == 0) {
            throw new RetryableConflictException("培养批次状态更新版本冲突");
        }
        batch.setStatus(target.name());
        batch.setVersion(versionBefore + 1);

        if (freeReactor) {
            AlgaeReactor reactor = loadReactor(reactorCode);
            reactor.setStatus(ReactorStatus.AVAILABLE.name());
            reactor.setOperatorCode(operator);
            if (reactorMapper.updateById(reactor) == 0) {
                throw new RetryableConflictException("培养罐释放版本冲突");
            }
        }
        int postedFeeds = 0;
        if (target == BatchStatus.COMPLETED) {
            // 完成培养时，未删除的补液事件统一落账，落账后即不可删除。
            postedFeeds = feedMapper.postByBatchId(batch.getId(), LocalDateTime.now());
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("batchNo", batchNo);
        snapshot.put("reactorCode", reactorCode);
        snapshot.put("fromStatus", current.name());
        snapshot.put("toStatus", target.name());
        snapshot.put("versionBefore", versionBefore);
        snapshot.put("versionAfter", versionBefore + 1);
        if (postedFeeds > 0) {
            snapshot.put("postedFeedCount", postedFeeds);
        }
        auditService.record("CULTURE_BATCH", "TRANSITION",
                "batchNo=" + batchNo + ",reactorCode=" + reactorCode,
                java.time.LocalDate.now(), snapshot);
        return batch;
    }

    /** 删除未验收批次：仅 CREATED 且无任何业务事件时允许，同时释放罐位。 */
    @Override
    @Transactional
    public void deleteBatch(String batchNo, String reactorCode) {
        CultureBatch batch = loadBatch(batchNo, reactorCode);
        if (BatchStatus.ACCEPTED.name().equals(batch.getStatus())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED, "已验收培养批次不能删除");
        }
        if (!BatchStatus.CREATED.name().equals(batch.getStatus())) {
            throw new BizException(BizErrorCode.ILLEGAL_STATUS,
                    "仅 CREATED 状态批次允许删除，当前状态: " + batch.getStatus());
        }
        long events = feedMapper.selectCount(new LambdaQueryWrapper<FeedEvent>()
                .eq(FeedEvent::getBatchId, batch.getId()))
                + samplingMapper.selectCount(new LambdaQueryWrapper<SamplingEvent>()
                .eq(SamplingEvent::getBatchId, batch.getId()))
                + harvestMapper.selectCount(new LambdaQueryWrapper<HarvestRecord>()
                .eq(HarvestRecord::getBatchId, batch.getId()));
        if (events > 0) {
            throw new BizException(BizErrorCode.ILLEGAL_STATUS, "批次已存在业务事件，不能删除");
        }
        batchMapper.deleteById(batch.getId());
        AlgaeReactor reactor = loadReactor(reactorCode);
        reactor.setStatus(ReactorStatus.AVAILABLE.name());
        reactor.setOperatorCode(BizRequestContext.get().getOperatorCode());
        reactorMapper.updateById(reactor);
        auditService.record("CULTURE_BATCH", "DELETE",
                "batchNo=" + batchNo + ",reactorCode=" + reactorCode,
                java.time.LocalDate.now(), batchNo + "@" + reactorCode);
    }

    /** 删除补液：已落账拒绝；未落账则回补体积，事件删除与批次修正同事务。 */
    @Override
    @Transactional
    public void deleteFeed(String feedNo) {
        FeedEvent event = feedMapper.selectOne(new LambdaQueryWrapper<FeedEvent>()
                .eq(FeedEvent::getFeedNo, feedNo));
        if (event == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "补液事件不存在: " + feedNo);
        }
        if (Boolean.TRUE.equals(event.getPosted())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "已落账补液记录不能删除: " + feedNo);
        }
        CultureBatch batch = loadBatchById(event.getBatchId());
        if (BatchStatus.ACCEPTED.name().equals(batch.getStatus())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "培养批次已验收，业务记录不能删除: " + feedNo);
        }
        AlgaeReactor reactor = loadReactor(batch.getReactorCode());
        int versionBefore = batch.getVersion();
        int rows = batchMapper.adjustVolumeOnly(batch.getId(), versionBefore,
                event.getFeedVolumeMl().negate(), reactor.getCapacityMl(),
                BizRequestContext.get().getOperatorCode());
        if (rows == 0) {
            throw new RetryableConflictException("补液回删体积修正版本冲突");
        }
        feedMapper.deleteById(event.getId());
        auditService.record("FEED", "DELETE",
                "feedNo=" + feedNo + ",batchNo=" + batch.getBatchNo()
                        + ",reactorCode=" + batch.getReactorCode(),
                event.getBizDate(), feedNo);
    }

    /** 删除采样：回补采样体积，同事务。 */
    @Override
    @Transactional
    public void deleteSampling(String samplingNo) {
        SamplingEvent event = samplingMapper.selectOne(new LambdaQueryWrapper<SamplingEvent>()
                .eq(SamplingEvent::getSamplingNo, samplingNo));
        if (event == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "采样事件不存在: " + samplingNo);
        }
        CultureBatch batch = loadBatchById(event.getBatchId());
        if (BatchStatus.ACCEPTED.name().equals(batch.getStatus())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "培养批次已验收，业务记录不能删除: " + samplingNo);
        }
        if (event.getSampleVolumeMl() != null
                && event.getSampleVolumeMl().compareTo(BigDecimal.ZERO) > 0) {
            AlgaeReactor reactor = loadReactor(batch.getReactorCode());
            int rows = batchMapper.adjustVolumeOnly(batch.getId(), batch.getVersion(),
                    event.getSampleVolumeMl(), reactor.getCapacityMl(),
                    BizRequestContext.get().getOperatorCode());
            if (rows == 0) {
                throw new RetryableConflictException("采样回删体积修正版本冲突");
            }
        }
        samplingMapper.deleteById(event.getId());
        auditService.record("SAMPLING", "DELETE", "samplingNo=" + samplingNo,
                event.getBizDate(), samplingNo);
    }

    /** 删除采收：已落账拒绝。 */
    @Override
    @Transactional
    public void deleteHarvest(String harvestNo) {
        HarvestRecord record = harvestMapper.selectOne(new LambdaQueryWrapper<HarvestRecord>()
                .eq(HarvestRecord::getHarvestNo, harvestNo));
        if (record == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "采收批次不存在: " + harvestNo);
        }
        if (Boolean.TRUE.equals(record.getPosted())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "已落账采收记录不能删除: " + harvestNo);
        }
        CultureBatch batch = loadBatchById(record.getBatchId());
        if (BatchStatus.ACCEPTED.name().equals(batch.getStatus())) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "培养批次已验收，业务记录不能删除: " + harvestNo);
        }
        AlgaeReactor reactor = loadReactor(batch.getReactorCode());
        int rows = batchMapper.adjustVolumeOnly(batch.getId(), batch.getVersion(),
                record.getHarvestVolumeMl(), reactor.getCapacityMl(),
                BizRequestContext.get().getOperatorCode());
        if (rows == 0) {
            throw new RetryableConflictException("采收回删体积修正版本冲突");
        }
        harvestMapper.deleteById(record.getId());
        auditService.record("HARVEST", "DELETE", "harvestNo=" + harvestNo,
                record.getBizDate(), harvestNo);
    }

    // ---- 内部辅助 ----

    private CultureBatch loadRunningBatch(String batchNo, String reactorCode) {
        CultureBatch batch = loadBatch(batchNo, reactorCode);
        if (!BatchStatus.RUNNING.name().equals(batch.getStatus())) {
            throw new BizException(BizErrorCode.ILLEGAL_STATUS,
                    "培养批次非 RUNNING 状态，不能登记业务事件，当前状态: "
                            + batch.getStatus());
        }
        return batch;
    }

    private CultureBatch loadBatch(String batchNo, String reactorCode) {
        CultureBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<CultureBatch>()
                .eq(CultureBatch::getBatchNo, batchNo)
                .eq(CultureBatch::getReactorCode, reactorCode));
        if (batch == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "培养批次不存在: (" + batchNo + ", " + reactorCode + ")");
        }
        return batch;
    }

    private CultureBatch loadBatchById(Long batchId) {
        CultureBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "培养批次不存在: id=" + batchId);
        }
        return batch;
    }

    private AlgaeReactor loadReactor(String reactorCode) {
        AlgaeReactor reactor = reactorMapper.selectOne(new LambdaQueryWrapper<AlgaeReactor>()
                .eq(AlgaeReactor::getReactorCode, reactorCode));
        if (reactor == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "培养罐不存在: " + reactorCode);
        }
        return reactor;
    }

    private void checkBizDate(CultureBatch batch, java.time.LocalDate bizDate) {
        if (bizDate.isBefore(batch.getInoculationDate())) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "业务日期不能早于接种日期 " + batch.getInoculationDate());
        }
    }

    private void checkPhRange(BigDecimal ph) {
        if (ph.compareTo(BigDecimal.valueOf(14)) > 0) {
            throw new BizException(BizErrorCode.INVALID_MEASUREMENT, "pH 取值应在 0~14 之间");
        }
    }

    /** 守卫更新影响 0 行：重读判定是版本冲突（可重试）还是体积越界（不可重试）。 */
    private void diagnoseGuardFailure(CultureBatch batch, int expectedVersion,
                                      BigDecimal delta, BigDecimal capacity,
                                      String boundMessage) {
        CultureBatch fresh = batchMapper.selectById(batch.getId());
        if (fresh == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "培养批次已不存在");
        }
        if (!fresh.getVersion().equals(expectedVersion)) {
            throw new RetryableConflictException("培养批次版本 " + expectedVersion
                    + " 已过期，最新版本 " + fresh.getVersion());
        }
        BigDecimal after = fresh.getCurrentVolumeMl().add(delta);
        if (after.compareTo(capacity) > 0 || after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(BizErrorCode.VOLUME_EXCEEDED,
                    boundMessage + "；当前体积 " + fresh.getCurrentVolumeMl()
                            + "mL，罐容量 " + capacity + "mL");
        }
        throw new RetryableConflictException("培养批次守卫更新失败，触发重试");
    }

    private BizException illegalTransition(BatchStatus from, BatchStatus to) {
        return new BizException(BizErrorCode.ILLEGAL_STATUS,
                "非法状态流转: " + from.name() + " -> " + to.name());
    }

    private Map<String, Object> volumeSnapshot(CultureBatch batch, String eventNo,
                                               String eventType, java.time.LocalDate bizDate,
                                               int versionBefore, BigDecimal delta,
                                               BigDecimal ph, BigDecimal od) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("batchNo", batch.getBatchNo());
        snapshot.put("reactorCode", batch.getReactorCode());
        snapshot.put("eventType", eventType);
        snapshot.put("eventNo", eventNo);
        snapshot.put("bizDate", bizDate.toString());
        snapshot.put("versionBefore", versionBefore);
        snapshot.put("versionAfter", versionBefore + 1);
        snapshot.put("volumeBeforeMl", batch.getCurrentVolumeMl());
        snapshot.put("volumeDeltaMl", delta);
        snapshot.put("volumeAfterMl", batch.getCurrentVolumeMl().add(delta));
        snapshot.put("phSnapshot", ph);
        snapshot.put("odSnapshot", od);
        return snapshot;
    }
}
