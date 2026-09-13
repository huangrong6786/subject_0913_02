package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.entity.AlgaeBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface AlgaeBatchMapper extends BaseMapper<AlgaeBatch> {

    /** 补液落账：原子累加补液量并刷新反应器最新光密度/pH，并发安全 */
    @Update("UPDATE t_algae_batch SET total_feed_l = total_feed_l + #{vol}, "
            + "latest_od = #{od}, latest_ph = #{ph}, "
            + "version = version + 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int applyFeed(@Param("id") Long id, @Param("vol") BigDecimal vol,
                  @Param("od") BigDecimal od, @Param("ph") BigDecimal ph);

    /** 采收登记：原子累加采收量，培养中批次自动转入采收中 */
    @Update("UPDATE t_algae_batch SET total_harvest_l = total_harvest_l + #{vol}, "
            + "latest_od = COALESCE(#{od}, latest_od), latest_ph = COALESCE(#{ph}, latest_ph), "
            + "status = CASE WHEN status = 'CULTIVATING' THEN 'HARVESTING' ELSE status END, "
            + "version = version + 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int applyHarvest(@Param("id") Long id, @Param("vol") BigDecimal vol,
                     @Param("od") BigDecimal od, @Param("ph") BigDecimal ph);

    /** 删除草稿采收时回滚累计采收量 */
    @Update("UPDATE t_algae_batch SET total_harvest_l = total_harvest_l - #{vol}, "
            + "version = version + 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int revertHarvest(@Param("id") Long id, @Param("vol") BigDecimal vol);
}
