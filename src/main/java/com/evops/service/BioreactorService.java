package com.evops.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BizContext;
import com.evops.common.BizException;
import com.evops.constant.BatchStatus;
import com.evops.constant.HarvestStatus;
import com.evops.constant.TankStatus;
import com.evops.dto.BatchCreateReq;
import com.evops.dto.FeedCreateReq;
import com.evops.dto.HarvestCreateReq;
import com.evops.dto.TankCreateReq;
import com.evops.entity.AlgaeBatch;
import com.evops.entity.BizEntity;
import com.evops.entity.CultureTank;
import com.evops.entity.FeedEvent;
import com.evops.entity.HarvestBatch;
import com.evops.mapper.AlgaeBatchMapper;
import com.evops.mapper.CultureTankMapper;
import com.evops.mapper.FeedEventMapper;
import com.evops.mapper.HarvestBatchMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微藻生物反应器培养-采收闭环。所有跨表写入都在同一事务内完成，
 * 并随记录保存请求号、操作者、业务时区与版本快照。
 */
@Service
public class BioreactorService {

    /** 批次允许的相邻状态流转 */
    private static final Map<String, String> BATCH_NEXT = new HashMap<>();

    static {
        BATCH_NEXT.put(BatchStatus.CULTIVATING, BatchStatus.HARVESTING);
        BATCH_NEXT.put(BatchStatus.HARVESTING, BatchStatus.CLOSED);
    }

    private final CultureTankMapper tankMapper;
    private final AlgaeBatchMapper batchMapper;
    private final FeedEventMapper feedMapper;
    private final HarvestBatchMapper harvestMapper;
    private final BizContext bizContext;
    private final ObjectMapper objectMapper;

    public BioreactorService(CultureTankMapper tankMapper, AlgaeBatchMapper batchMapper,
                             FeedEventMapper feedMapper, HarvestBatchMapper harvestMapper,
                             BizContext bizContext, ObjectMapper objectMapper) {
        this.tankMapper = tankMapper;
        this.batchMapper = batchMapper;
        this.feedMapper = feedMapper;
        this.harvestMapper = harvestMapper;
        this.bizContext = bizContext;
        this.objectMapper = objectMapper;
    }

    // ---------------- 培养罐 ----------------

    @Transactional
    public CultureTank createTank(TankCreateReq req) {
        CultureTank tank = new CultureTank();
        tank.setTankCode(req.getTankCode());
        tank.setTankName(req.getTankName());
        tank.setVolumeL(req.getVolumeL());
        tank.setStatus(TankStatus.IDLE);
        fillAudit(tank, req.getRequestNo());
        tank.setSnapshotJson(snapshotOf("tank", tank));
        try {
            tankMapper.insert(tank);
        } catch (DuplicateKeyException e) {
            throw new BizException("培养罐编号或请求号已存在：" + req.getTankCode());
        }
        return tank;
    }

    public List<CultureTank> listTanks() {
        return tankMapper.selectList(new QueryWrapper<CultureTank>().orderByAsc("id"));
    }

    // ---------------- 藻种批次 ----------------

    @Transactional
    public AlgaeBatch createBatch(BatchCreateReq req) {
        CultureTank tank = requireTank(req.getTankId());
        if (!TankStatus.IDLE.equals(tank.getStatus())) {
            throw new BizException("培养罐 " + tank.getTankCode() + " 状态为 " + tank.getStatus() + "，不能接种新批次");
        }
        AlgaeBatch batch = new AlgaeBatch();
        batch.setBatchNo(req.getBatchNo());
        batch.setSpecies(req.getSpecies());
        batch.setTankId(tank.getId());
        batch.setInoculationDate(req.getInoculationDate());
        batch.setInitialOd(req.getInitialOd());
        batch.setLatestOd(req.getInitialOd());
        batch.setTotalFeedL(BigDecimal.ZERO);
        batch.setTotalHarvestL(BigDecimal.ZERO);
        batch.setStatus(BatchStatus.CULTIVATING);
        fillAudit(batch, req.getRequestNo());
        batch.setSnapshotJson(snapshotOf("batch", batch));
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            throw new BizException("藻种批次号或请求号已存在：" + req.getBatchNo());
        }
        // 同事务占用培养罐（乐观锁）
        tank.setStatus(TankStatus.OCCUPIED);
        if (tankMapper.updateById(tank) == 0) {
            throw new BizException("培养罐状态被并发修改，请重试");
        }
        return batch;
    }

    public List<AlgaeBatch> listBatches(String status) {
        QueryWrapper<AlgaeBatch> query = new QueryWrapper<>();
        if (status != null && !status.trim().isEmpty()) {
            query.eq("status", status.trim());
        }
        return batchMapper.selectList(query.orderByAsc("id"));
    }

    /** 关联查询：批次 + 培养罐 + 补液事件 + 采收批次 */
    public Map<String, Object> batchDetail(Long id) {
        AlgaeBatch batch = requireBatch(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("batch", batch);
        detail.put("tank", tankMapper.selectById(batch.getTankId()));
        detail.put("feedEvents", feedMapper.selectList(
                new QueryWrapper<FeedEvent>().eq("batch_id", id).orderByAsc("feed_date")));
        detail.put("harvestBatches", harvestMapper.selectList(
                new QueryWrapper<HarvestBatch>().eq("batch_id", id).orderByAsc("harvest_date")));
        return detail;
    }

    @Transactional
    public AlgaeBatch transitionBatch(Long id, String target) {
        AlgaeBatch batch = requireBatch(id);
        String expected = BATCH_NEXT.get(batch.getStatus());
        if (expected == null || !expected.equals(target)) {
            throw new BizException("批次状态不允许从 " + batch.getStatus() + " 流转到 " + target);
        }
        batch.setStatus(target);
        if (batchMapper.updateById(batch) == 0) {
            throw new BizException("批次已被并发修改，请刷新后重试");
        }
        if (BatchStatus.CLOSED.equals(target)) {
            // 同事务释放培养罐
            CultureTank tank = requireTank(batch.getTankId());
            tank.setStatus(TankStatus.IDLE);
            if (tankMapper.updateById(tank) == 0) {
                throw new BizException("培养罐已被并发修改，请刷新后重试");
            }
        }
        return batch;
    }

    // ---------------- 补液事件 ----------------

    @Transactional
    public FeedEvent createFeed(FeedCreateReq req) {
        String requestNo = bizContext.requestNo(req.getRequestNo());
        // 幂等：同一请求号直接返回已落账记录
        FeedEvent existed = feedMapper.selectOne(new QueryWrapper<FeedEvent>().eq("request_no", requestNo));
        if (existed != null) {
            return existed;
        }
        AlgaeBatch batch = requireBatch(req.getBatchId());
        if (BatchStatus.CLOSED.equals(batch.getStatus())) {
            throw new BizException("批次已关闭，不能补液");
        }
        FeedEvent event = new FeedEvent();
        event.setFeedNo(req.getFeedNo());
        event.setBatchId(batch.getId());
        event.setTankId(batch.getTankId());
        event.setFeedDate(req.getFeedDate());
        event.setFeedVolumeL(req.getFeedVolumeL());
        event.setOpticalDensity(req.getOpticalDensity());
        event.setPh(req.getPh());
        fillAudit(event, requestNo);
        try {
            feedMapper.insert(event);
        } catch (DuplicateKeyException e) {
            throw new BizException("补液事件编号已存在：" + req.getFeedNo());
        }
        // 同事务原子累加批次补液量并刷新反应器读数
        if (batchMapper.applyFeed(batch.getId(), req.getFeedVolumeL(), req.getOpticalDensity(), req.getPh()) == 0) {
            throw new BizException("批次不存在或已被并发修改");
        }
        AlgaeBatch after = requireBatch(batch.getId());
        String snapshot = snapshotOf("batch", after, "feedEvent", event);
        feedMapper.updateSnapshot(event.getId(), snapshot);
        event.setSnapshotJson(snapshot);
        return event;
    }

    public List<FeedEvent> listFeeds(Long batchId) {
        return feedMapper.selectList(new QueryWrapper<FeedEvent>()
                .eq(batchId != null, "batch_id", batchId).orderByAsc("feed_date"));
    }

    /** 补液事件创建即落账，落账记录不能直接删除 */
    public void deleteFeed(Long id) {
        FeedEvent event = feedMapper.selectById(id);
        if (event == null) {
            throw new BizException("补液事件不存在：" + id);
        }
        throw new BizException("补液事件 " + event.getFeedNo() + " 已落账，不能直接删除");
    }

    // ---------------- 采收批次 ----------------

    @Transactional
    public HarvestBatch createHarvest(HarvestCreateReq req) {
        String requestNo = bizContext.requestNo(req.getRequestNo());
        HarvestBatch existed = harvestMapper.selectOne(new QueryWrapper<HarvestBatch>().eq("request_no", requestNo));
        if (existed != null) {
            return existed;
        }
        AlgaeBatch batch = requireBatch(req.getBatchId());
        if (BatchStatus.CLOSED.equals(batch.getStatus())) {
            throw new BizException("批次已关闭，不能登记采收");
        }
        HarvestBatch harvest = new HarvestBatch();
        harvest.setHarvestNo(req.getHarvestNo());
        harvest.setBatchId(batch.getId());
        harvest.setTankId(batch.getTankId());
        harvest.setHarvestDate(req.getHarvestDate());
        harvest.setHarvestVolumeL(req.getHarvestVolumeL());
        harvest.setOpticalDensity(req.getOpticalDensity());
        harvest.setPh(req.getPh());
        harvest.setStatus(HarvestStatus.DRAFT);
        fillAudit(harvest, requestNo);
        try {
            harvestMapper.insert(harvest);
        } catch (DuplicateKeyException e) {
            throw new BizException("采收批次号已存在：" + req.getHarvestNo());
        }
        // 同事务原子累加批次采收量，培养中批次自动转入采收中
        if (batchMapper.applyHarvest(batch.getId(), req.getHarvestVolumeL(),
                req.getOpticalDensity(), req.getPh()) == 0) {
            throw new BizException("批次不存在或已被并发修改");
        }
        AlgaeBatch after = requireBatch(batch.getId());
        String snapshot = snapshotOf("batch", after, "harvestBatch", harvest);
        harvestMapper.updateSnapshot(harvest.getId(), snapshot);
        harvest.setSnapshotJson(snapshot);
        return harvest;
    }

    public List<HarvestBatch> listHarvests(Long batchId) {
        return harvestMapper.selectList(new QueryWrapper<HarvestBatch>()
                .eq(batchId != null, "batch_id", batchId).orderByAsc("harvest_date"));
    }

    @Transactional
    public HarvestBatch postHarvest(Long id) {
        HarvestBatch harvest = requireHarvest(id);
        if (!HarvestStatus.DRAFT.equals(harvest.getStatus())) {
            throw new BizException("仅草稿状态的采收批次可以落账，当前状态：" + harvest.getStatus());
        }
        harvest.setStatus(HarvestStatus.POSTED);
        harvest.setPostedTime(LocalDateTime.now());
        if (harvestMapper.updateById(harvest) == 0) {
            throw new BizException("采收批次已被并发修改，请刷新后重试");
        }
        return harvest;
    }

    @Transactional
    public HarvestBatch acceptHarvest(Long id) {
        HarvestBatch harvest = requireHarvest(id);
        if (!HarvestStatus.POSTED.equals(harvest.getStatus())) {
            throw new BizException("仅已落账的采收批次可以验收，当前状态：" + harvest.getStatus());
        }
        harvest.setStatus(HarvestStatus.ACCEPTED);
        harvest.setAcceptedTime(LocalDateTime.now());
        if (harvestMapper.updateById(harvest) == 0) {
            throw new BizException("采收批次已被并发修改，请刷新后重试");
        }
        return harvest;
    }

    /** 已落账或已验收的采收批次不能直接删除；草稿删除时同事务回滚批次累计采收量 */
    @Transactional
    public void deleteHarvest(Long id) {
        HarvestBatch harvest = requireHarvest(id);
        if (!HarvestStatus.DRAFT.equals(harvest.getStatus())) {
            throw new BizException("采收批次 " + harvest.getHarvestNo() + " 已落账或已验收，不能直接删除");
        }
        harvestMapper.deleteById(id);
        batchMapper.revertHarvest(harvest.getBatchId(), harvest.getHarvestVolumeL());
    }

    // ---------------- 内部工具 ----------------

    private CultureTank requireTank(Long id) {
        CultureTank tank = tankMapper.selectById(id);
        if (tank == null) {
            throw new BizException("培养罐不存在：" + id);
        }
        return tank;
    }

    private AlgaeBatch requireBatch(Long id) {
        AlgaeBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BizException("藻种批次不存在：" + id);
        }
        return batch;
    }

    private HarvestBatch requireHarvest(Long id) {
        HarvestBatch harvest = harvestMapper.selectById(id);
        if (harvest == null) {
            throw new BizException("采收批次不存在：" + id);
        }
        return harvest;
    }

    private void fillAudit(BizEntity entity, String requestNo) {
        entity.setRequestNo(bizContext.requestNo(requestNo));
        entity.setOperator(bizContext.operator());
        entity.setBizTimezone(bizContext.timezone());
        entity.setVersion(0);
    }

    private String snapshotOf(Object... keyValues) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            snapshot.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return "{}";
        }
    }
}
