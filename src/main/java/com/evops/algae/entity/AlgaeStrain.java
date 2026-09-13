package com.evops.algae.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 藻种批次（接种母批次）。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_algae_strain")
public class AlgaeStrain extends BaseEntity {
    /** 藻种批次业务键，全局唯一。 */
    private String strainNo;
    private String strainName;
    /** 藻种学名。 */
    private String species;
    /** 来源机构。 */
    private String sourceOrg;
    /** 是否已验收；验收后不得删除。 */
    private Boolean accepted;
    private LocalDate acceptedDate;
    private String acceptRemark;
    /** 操作者业务编码（审计用，区别于 BaseEntity.createBy 数字主键）。 */
    private String operatorCode;
    @Version
    private Integer version;
}
