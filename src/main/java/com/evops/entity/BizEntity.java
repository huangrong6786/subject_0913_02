package com.evops.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务实体基类：在 BaseEntity 审计列之上，追加每次跨表写入必须保存的
 * 请求号、操作者、业务时区、版本快照与乐观锁版本号。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BizEntity extends BaseEntity {
    /** 请求号（幂等键，唯一） */
    private String requestNo;

    /** 操作者 */
    @TableField("biz_operator")
    private String operator;

    /** 业务时区 */
    private String bizTimezone;

    /** 写入时刻关联对象的版本快照（JSON） */
    private String snapshotJson;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
