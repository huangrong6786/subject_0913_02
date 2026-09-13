package com.evops.algae.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evops.algae.audit.AuditService;
import com.evops.algae.common.BizErrorCode;
import com.evops.algae.common.BizException;
import com.evops.algae.context.BizRequestContext;
import com.evops.algae.dto.ReactorCreateRequest;
import com.evops.algae.entity.AlgaeReactor;
import com.evops.algae.entity.CultureBatch;
import com.evops.algae.enums.BatchStatus;
import com.evops.algae.enums.ReactorStatus;
import com.evops.algae.mapper.AlgaeReactorMapper;
import com.evops.algae.mapper.CultureBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReactorService {

    private final AlgaeReactorMapper reactorMapper;
    private final CultureBatchMapper batchMapper;
    private final AuditService auditService;

    public ReactorService(AlgaeReactorMapper reactorMapper,
                          CultureBatchMapper batchMapper,
                          AuditService auditService) {
        this.reactorMapper = reactorMapper;
        this.batchMapper = batchMapper;
        this.auditService = auditService;
    }

    @Transactional
    public AlgaeReactor create(ReactorCreateRequest request) {
        boolean exists = reactorMapper.exists(new LambdaQueryWrapper<AlgaeReactor>()
                .eq(AlgaeReactor::getReactorCode, request.getReactorCode()));
        if (exists) {
            throw new BizException(BizErrorCode.BIZ_KEY_DUPLICATED,
                    "培养罐编码已存在: " + request.getReactorCode());
        }
        AlgaeReactor reactor = new AlgaeReactor();
        reactor.setReactorCode(request.getReactorCode());
        reactor.setReactorName(request.getReactorName());
        reactor.setCabinetPosition(request.getCabinetPosition());
        reactor.setCapacityMl(request.getCapacityMl());
        reactor.setStatus(ReactorStatus.AVAILABLE.name());
        reactor.setOperatorCode(BizRequestContext.get().getOperatorCode());
        reactor.setVersion(0);
        reactorMapper.insert(reactor);
        auditService.record("REACTOR", "CREATE",
                "reactorCode=" + reactor.getReactorCode(), LocalDate.now(), reactor);
        return reactor;
    }

    /** 柜位被在制培养批次占用时不能删除。 */
    @Transactional
    public void delete(String reactorCode) {
        AlgaeReactor reactor = requireByCode(reactorCode);
        boolean occupied = batchMapper.exists(new LambdaQueryWrapper<CultureBatch>()
                .eq(CultureBatch::getReactorCode, reactorCode)
                .in(CultureBatch::getStatus,
                        BatchStatus.CREATED.name(),
                        BatchStatus.RUNNING.name()));
        if (occupied) {
            throw new BizException(BizErrorCode.RECORD_LOCKED,
                    "培养罐存在在制批次，不能删除: " + reactorCode);
        }
        reactorMapper.deleteById(reactor.getId());
        auditService.record("REACTOR", "DELETE", "reactorCode=" + reactorCode,
                LocalDate.now(), reactorCode);
    }

    public AlgaeReactor get(String reactorCode) {
        return requireByCode(reactorCode);
    }

    public List<AlgaeReactor> list() {
        return reactorMapper.selectList(new LambdaQueryWrapper<AlgaeReactor>()
                .orderByAsc(AlgaeReactor::getReactorCode));
    }

    private AlgaeReactor requireByCode(String reactorCode) {
        AlgaeReactor reactor = reactorMapper.selectOne(new LambdaQueryWrapper<AlgaeReactor>()
                .eq(AlgaeReactor::getReactorCode, reactorCode));
        if (reactor == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "培养罐不存在: " + reactorCode);
        }
        return reactor;
    }
}
