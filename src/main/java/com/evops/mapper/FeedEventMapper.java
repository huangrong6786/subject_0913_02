package com.evops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evops.entity.FeedEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FeedEventMapper extends BaseMapper<FeedEvent> {

    /** 跨表写入完成后回填版本快照，不触碰乐观锁版本 */
    @Update("UPDATE t_feed_event SET snapshot_json = #{snapshot} WHERE id = #{id}")
    int updateSnapshot(@Param("id") Long id, @Param("snapshot") String snapshot);
}
