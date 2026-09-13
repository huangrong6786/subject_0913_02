package com.evops.algae.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.algae.entity.FeedEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface FeedEventMapper extends BaseMapper<FeedEvent> {

    /** 批次完成培养时把其全部补液事件落账，返回受影响行数。 */
    @Update("UPDATE t_algae_feed_event SET posted = TRUE, posted_at = #{postedAt}, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE batch_id = #{batchId} AND posted = FALSE")
    int postByBatchId(@Param("batchId") Long batchId,
                      @Param("postedAt") LocalDateTime postedAt);
}
