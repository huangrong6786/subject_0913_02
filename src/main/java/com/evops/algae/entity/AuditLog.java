package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 业务审计日志。每一次跨表写入都在同一事务内留痕：
 * 请求号、操作者、业务时区、业务日期与版本快照（JSON）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_audit_log")
public class AuditLog extends BaseEntity {
    private String requestNo;
    private String operatorCode;
    private String bizTimezone;
    /** 业务对象，如 CULTURE_BATCH / FEED / SAMPLING / HARVEST。 */
    private String bizObject;
    /** 业务动作，如 CREATE / FEED / SAMPLE / HARVEST / TRANSITION / DELETE。 */
    private String bizAction;
    /** 涉及的业务键，便于检索。 */
    private String bizKeys;
    private LocalDate bizDate;
    /** 写入时聚合根版本快照（JSON 文本）。 */
    private String versionSnapshot;
}
