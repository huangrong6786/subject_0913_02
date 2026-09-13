package com.evops.algae.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.evops.algae.context.BizRequestContext;
import com.evops.algae.entity.AuditLog;
import com.evops.algae.mapper.AuditLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 业务审计记录器。调用方处于业务事务中时，审计写入沿用同一事务
 * （默认 REQUIRED 传播），业务回滚则审计一并回滚，杜绝跨表半写。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String bizObject, String bizAction, String bizKeys,
                       LocalDate bizDate, Object versionSnapshot) {
        BizRequestContext.RequestInfo info = BizRequestContext.get();
        AuditLog auditLog = new AuditLog();
        auditLog.setRequestNo(info.getRequestNo());
        auditLog.setOperatorCode(info.getOperatorCode());
        auditLog.setBizTimezone(info.getBizTimezone());
        auditLog.setBizObject(bizObject);
        auditLog.setBizAction(bizAction);
        auditLog.setBizKeys(bizKeys);
        auditLog.setBizDate(bizDate);
        auditLog.setVersionSnapshot(toJson(versionSnapshot));
        auditLogMapper.insert(auditLog);
    }

    /** 凭请求号回溯同一请求内的全部跨表写入留痕。 */
    public List<AuditLog> listByRequestNo(String requestNo) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getRequestNo, requestNo)
                .orderByAsc(AuditLog::getId));
    }

    private String toJson(Object snapshot) {        if (snapshot == null) {
            return null;
        }
        if (snapshot instanceof String) {
            return (String) snapshot;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            log.warn("版本快照序列化失败: {}", ex.getMessage());
            return null;
        }
    }
}
