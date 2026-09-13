package com.evops.algae.dto;

import com.evops.algae.entity.CultureBatch;
import com.evops.algae.entity.FeedEvent;
import com.evops.algae.entity.HarvestRecord;
import com.evops.algae.entity.SamplingEvent;
import lombok.Data;

import java.util.List;

/** 培养批次聚合视图：批次 + 补液 + 采样 + 采收 的关联查询结果。 */
@Data
public class BatchDetailVO {
    private CultureBatch batch;
    private String strainName;
    private String reactorName;
    private String cabinetPosition;
    private List<FeedEvent> feeds;
    private List<SamplingEvent> samplings;
    private List<HarvestRecord> harvests;
}
