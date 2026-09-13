package com.evops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 微藻生物反应器培养-采收闭环集成测试。
 * 覆盖：完整闭环、业务键唯一、落账/验收删除保护、状态流转、5 路并发写入。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BioreactorApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    // ---------- 完整闭环 ----------

    @Test
    void fullClosedLoop() throws Exception {
        long tankId = createTank("TANK-A", "1号培养罐");
        long batchId = createBatch("BATCH-A", tankId);

        // 补液（记录光密度/pH/补液量）
        JsonNode feed = postJson("/api/algae/feeds", feedReq("FEED-A-1", batchId, "12.5"));
        assertTrue(feed.get("success").asBoolean(), feed.toString());
        assertEquals("tester", feed.at("/data/operator").asText());
        assertEquals("Asia/Shanghai", feed.at("/data/bizTimezone").asText());
        assertTrue(feed.at("/data/snapshotJson").asText().contains("BATCH-A"));

        // 采收 -> 落账 -> 验收
        JsonNode harvest = postJson("/api/algae/harvests", harvestReq("HARV-A-1", batchId, "30"));
        assertTrue(harvest.get("success").asBoolean(), harvest.toString());
        long harvestId = harvest.at("/data/id").asLong();
        assertEquals("DRAFT", harvest.at("/data/status").asText());
        assertTrue(postEmpty("/api/algae/harvests/" + harvestId + "/post").get("success").asBoolean());
        assertTrue(postEmpty("/api/algae/harvests/" + harvestId + "/accept").get("success").asBoolean());

        // 已验收记录不能直接删除
        JsonNode delAccepted = deleteJson("/api/algae/harvests/" + harvestId);
        assertFalse(delAccepted.get("success").asBoolean());

        // 关联查询：批次 + 罐 + 补液 + 采收
        JsonNode detail = getJson("/api/algae/batches/" + batchId);
        assertTrue(detail.get("success").asBoolean());
        assertEquals("TANK-A", detail.at("/data/tank/tankCode").asText());
        assertEquals(1, detail.at("/data/feedEvents").size());
        assertEquals(1, detail.at("/data/harvestBatches").size());
        assertEquals("HARVESTING", detail.at("/data/batch/status").asText());
        assertEquals(0, new BigDecimal("12.500").compareTo(detail.at("/data/batch/totalFeedL").decimalValue()));
        assertEquals(0, new BigDecimal("30.000").compareTo(detail.at("/data/batch/totalHarvestL").decimalValue()));

        // 关闭批次后培养罐释放
        assertTrue(postEmpty("/api/algae/batches/" + batchId + "/transition?target=CLOSED").get("success").asBoolean());
        JsonNode tanks = getJson("/api/algae/tanks");
        for (JsonNode tank : tanks.at("/data")) {
            if ("TANK-A".equals(tank.get("tankCode").asText())) {
                assertEquals("IDLE", tank.get("status").asText());
            }
        }
    }

    // ---------- 业务键唯一 ----------

    @Test
    void duplicateBusinessKeyRejected() throws Exception {
        createTank("TANK-DUP", "重复罐");
        JsonNode dupTank = postJson("/api/algae/tanks", tankReq("TANK-DUP", "重复罐2"));
        assertFalse(dupTank.get("success").asBoolean());

        long tankId = createTank("TANK-DUP2", "备用罐");
        createBatch("BATCH-DUP", tankId);
        // 占用中的罐不能接种
        JsonNode busy = postJson("/api/algae/batches", batchReq("BATCH-DUP2", tankId));
        assertFalse(busy.get("success").asBoolean());
        // 批次号唯一
        long tankId2 = createTank("TANK-DUP3", "备用罐2");
        JsonNode dupBatch = postJson("/api/algae/batches", batchReq("BATCH-DUP", tankId2));
        assertFalse(dupBatch.get("success").asBoolean());
    }

    // ---------- 落账/验收删除保护 ----------

    @Test
    void postedRecordsCannotBeDeleted() throws Exception {
        long tankId = createTank("TANK-P", "保护罐");
        long batchId = createBatch("BATCH-P", tankId);

        // 补液事件创建即落账，不能删除
        JsonNode feed = postJson("/api/algae/feeds", feedReq("FEED-P-1", batchId, "5"));
        long feedId = feed.at("/data/id").asLong();
        JsonNode delFeed = deleteJson("/api/algae/feeds/" + feedId);
        assertFalse(delFeed.get("success").asBoolean());

        // 草稿采收可删，落账后不可删
        JsonNode harvest = postJson("/api/algae/harvests", harvestReq("HARV-P-1", batchId, "8"));
        long harvestId = harvest.at("/data/id").asLong();
        assertTrue(postEmpty("/api/algae/harvests/" + harvestId + "/post").get("success").asBoolean());
        JsonNode delPosted = deleteJson("/api/algae/harvests/" + harvestId);
        assertFalse(delPosted.get("success").asBoolean());

        // 草稿删除成功且回滚累计采收量
        JsonNode draft = postJson("/api/algae/harvests", harvestReq("HARV-P-2", batchId, "3"));
        long draftId = draft.at("/data/id").asLong();
        assertTrue(deleteJson("/api/algae/harvests/" + draftId).get("success").asBoolean());
        JsonNode detail = getJson("/api/algae/batches/" + batchId);
        assertEquals(0, new BigDecimal("8.000").compareTo(detail.at("/data/batch/totalHarvestL").decimalValue()));
    }

    // ---------- 状态流转 ----------

    @Test
    void statusTransitionRules() throws Exception {
        long tankId = createTank("TANK-S", "流转罐");
        long batchId = createBatch("BATCH-S", tankId);

        // 不允许跨级流转
        JsonNode jump = postEmpty("/api/algae/batches/" + batchId + "/transition?target=CLOSED");
        assertFalse(jump.get("success").asBoolean());
        // 合法流转
        assertTrue(postEmpty("/api/algae/batches/" + batchId + "/transition?target=HARVESTING").get("success").asBoolean());
        // 不允许逆向/重复流转
        JsonNode back = postEmpty("/api/algae/batches/" + batchId + "/transition?target=HARVESTING");
        assertFalse(back.get("success").asBoolean());
        assertTrue(postEmpty("/api/algae/batches/" + batchId + "/transition?target=CLOSED").get("success").asBoolean());
        // 已关闭批次不能补液
        JsonNode feed = postJson("/api/algae/feeds", feedReq("FEED-S-1", batchId, "1"));
        assertFalse(feed.get("success").asBoolean());
    }

    // ---------- 5 路并发写入 ----------

    @Test
    void concurrentFeedWrites() throws Exception {
        long tankId = createTank("TANK-C", "并发罐");
        long batchId = createBatch("BATCH-C", tankId);

        int n = 5;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<JsonNode>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return postJson("/api/algae/feeds", feedReq("FEED-C-" + idx, batchId, "10"));
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        for (Future<JsonNode> future : futures) {
            JsonNode resp = future.get(30, TimeUnit.SECONDS);
            assertTrue(resp.get("success").asBoolean(), resp.toString());
        }
        pool.shutdownNow();

        // 5 次并发补液全部落账：累计 50L，版本号递增 5，请求号各不相同
        JsonNode detail = getJson("/api/algae/batches/" + batchId);
        assertEquals(0, new BigDecimal("50.000").compareTo(detail.at("/data/batch/totalFeedL").decimalValue()));
        assertEquals(n, detail.at("/data/batch/version").asInt());
        assertEquals(n, detail.at("/data/feedEvents").size());
        List<String> requestNos = new ArrayList<>();
        for (JsonNode event : detail.at("/data/feedEvents")) {
            requestNos.add(event.get("requestNo").asText());
        }
        assertEquals(n, requestNos.stream().distinct().count());
    }

    // ---------- 请求辅助 ----------

    private long createTank(String code, String name) throws Exception {
        JsonNode resp = postJson("/api/algae/tanks", tankReq(code, name));
        assertTrue(resp.get("success").asBoolean(), resp.toString());
        return resp.at("/data/id").asLong();
    }

    private long createBatch(String batchNo, long tankId) throws Exception {
        JsonNode resp = postJson("/api/algae/batches", batchReq(batchNo, tankId));
        assertTrue(resp.get("success").asBoolean(), resp.toString());
        return resp.at("/data/id").asLong();
    }

    private Map<String, Object> tankReq(String code, String name) {
        Map<String, Object> req = new HashMap<>();
        req.put("tankCode", code);
        req.put("tankName", name);
        req.put("volumeL", new BigDecimal("500"));
        req.put("requestNo", "REQ-" + code);
        return req;
    }

    private Map<String, Object> batchReq(String batchNo, long tankId) {
        Map<String, Object> req = new HashMap<>();
        req.put("batchNo", batchNo);
        req.put("species", "小球藻 Chlorella vulgaris");
        req.put("tankId", tankId);
        req.put("inoculationDate", "2026-09-01");
        req.put("initialOd", new BigDecimal("0.100"));
        req.put("requestNo", "REQ-" + batchNo);
        return req;
    }

    private Map<String, Object> feedReq(String feedNo, long batchId, String volume) {
        Map<String, Object> req = new HashMap<>();
        req.put("feedNo", feedNo);
        req.put("batchId", batchId);
        req.put("feedDate", "2026-09-10T08:30:00");
        req.put("feedVolumeL", new BigDecimal(volume));
        req.put("opticalDensity", new BigDecimal("1.250"));
        req.put("ph", new BigDecimal("7.20"));
        req.put("requestNo", "REQ-" + feedNo);
        return req;
    }

    private Map<String, Object> harvestReq(String harvestNo, long batchId, String volume) {
        Map<String, Object> req = new HashMap<>();
        req.put("harvestNo", harvestNo);
        req.put("batchId", batchId);
        req.put("harvestDate", "2026-09-12");
        req.put("harvestVolumeL", new BigDecimal(volume));
        req.put("opticalDensity", new BigDecimal("1.800"));
        req.put("ph", new BigDecimal("7.50"));
        req.put("requestNo", "REQ-" + harvestNo);
        return req;
    }

    private JsonNode postJson(String url, Object body) throws Exception {
        String resp = mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator", "tester")
                        .header("X-Biz-Timezone", "Asia/Shanghai")
                        .content(om.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp);
    }

    private JsonNode postEmpty(String url) throws Exception {
        String resp = mvc.perform(post(url)
                        .header("X-Operator", "tester")
                        .header("X-Biz-Timezone", "Asia/Shanghai"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(resp);
    }

    private JsonNode getJson(String url) throws Exception {
        String resp = mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
        return om.readTree(resp);
    }

    private JsonNode deleteJson(String url) throws Exception {
        String resp = mvc.perform(delete(url)).andReturn().getResponse().getContentAsString();
        return om.readTree(resp);
    }
}
