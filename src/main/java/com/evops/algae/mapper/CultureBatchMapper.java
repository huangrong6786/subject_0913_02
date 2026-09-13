package com.evops.algae.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.algae.entity.CultureBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface CultureBatchMapper extends BaseMapper<CultureBatch> {

    /**
     * 体积变更与 pH/OD 快照更新（补液 delta 为正、采样/采收为负）。
     * 单条 SQL 同时守住乐观锁版本、罐容量上限和体积非负下限，
     * 杜绝并发补液超灌或并发采收超取。影响 0 行时由服务层判别是版本冲突还是越界。
     */
    @Update("UPDATE t_algae_culture_batch SET " +
            "current_volume_ml = current_volume_ml + #{deltaMl}, " +
            "latest_ph = #{ph}, latest_od = #{od}, latest_measure_date = #{bizDate}, " +
            "operator_code = #{operatorCode}, version = version + 1, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND version = #{expectedVersion} " +
            "AND current_volume_ml + #{deltaMl} BETWEEN 0 AND #{capacityMl}")
    int applyVolumeChange(@Param("id") Long id,
                          @Param("expectedVersion") Integer expectedVersion,
                          @Param("deltaMl") BigDecimal deltaMl,
                          @Param("capacityMl") BigDecimal capacityMl,
                          @Param("ph") BigDecimal ph,
                          @Param("od") BigDecimal od,
                          @Param("bizDate") LocalDate bizDate,
                          @Param("operatorCode") String operatorCode);

    /** 仅状态流转时的版本守卫更新（不改变体积）。 */
    @Update("UPDATE t_algae_culture_batch SET status = #{targetStatus}, " +
            "operator_code = #{operatorCode}, version = version + 1, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND version = #{expectedVersion}")
    int updateStatus(@Param("id") Long id,
                     @Param("expectedVersion") Integer expectedVersion,
                     @Param("targetStatus") String targetStatus,
                     @Param("operatorCode") String operatorCode);

    /** 回删事件时修正体积（不动 pH/OD 快照），同样带版本与上下限守卫。 */
    @Update("UPDATE t_algae_culture_batch SET " +
            "current_volume_ml = current_volume_ml + #{deltaMl}, " +
            "operator_code = #{operatorCode}, version = version + 1, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id} AND version = #{expectedVersion} " +
            "AND current_volume_ml + #{deltaMl} BETWEEN 0 AND #{capacityMl}")
    int adjustVolumeOnly(@Param("id") Long id,
                         @Param("expectedVersion") Integer expectedVersion,
                         @Param("deltaMl") BigDecimal deltaMl,
                         @Param("capacityMl") BigDecimal capacityMl,
                         @Param("operatorCode") String operatorCode);
}
