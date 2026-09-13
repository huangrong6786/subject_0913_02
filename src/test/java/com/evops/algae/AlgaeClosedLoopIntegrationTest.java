package com.evops.algae;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 微藻培养与采收闭环集成测试（REST 全链路）：
 * 覆盖 2+ 业务对象、3 个业务日期、5 个并发写入请求、同事务跨表写入、
 * 复合业务键唯一、状态机、删除保护与审计（请求号/操作者/时区/版本快照）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlgaeClosedLoopIntegrationTest {

    private static final String BASIC_AUTH = "Basic "
            + Base64.getEncoder().encodeToString(
            "bootstrap:bootstrap".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private final LocalDate inoculationDate = LocalDate.of(2026, 9, 1);
    private final LocalDate feedDate = LocalDate.of(2026, 9, 5);
    private final LocalDate harvestDate = LocalDate.of(2026, 9, 10);

    @BeforeEach
    void setUp() throws Exception {
        // 每次测试使用唯一业务键，内存库在类级别共享。
        String stamp = String.valueOf(System.nanoTime());
        postJson("/api/algae/strains", "{"
                + "\"strainNo\":\"ST-" + stamp + "\","
                + "\"strainName\":\"小球藻-" + stamp + "\","
                + "\"species\":\"Chlorella vulgaris\","
                + "\"sourceOrg\":\"藻类保种中心\"}", "REQ-STRAIN-" + stamp, "breeder");
        postJson("/api/algae/reactors", "{"
                + "\"reactorCode\":\"RC-" + stamp + "\","
                + "\"reactorName\":\"管式反应器-" + stamp + "\","
                + "\"cabinetPosition\":\"A-01-" + stamp + "\","
                + "\"capacityMl\":1000}", "REQ-REACTOR-" + stamp, "engineer");
        System.setProperty("test.stamp", stamp);
    }

    private String stamp() {
        return System.getProperty("test.stamp");
    }

    @Test
    void fullClosedLoop_concurrency_keys_status_audit() throws Exception {
        String stamp = stamp();
        String strainNo = "ST-" + stamp;
        String reactorCode = "RC-" + stamp;
        String batchNo = "B-" + stamp;

        // ---- 1. 建立培养批次（复合业务键 batchNo + reactorCode）----
        String createBatchBody = "{"
                + "\"batchNo\":\"" + batchNo + "\","
                + "\"reactorCode\":\"" + reactorCode + "\","
                + "\"strainNo\":\"" + strainNo + "\","
                + "\"initialVolumeMl\":100,"
                + "\"inoculationDate\":\"" + inoculationDate + "\","
                + "\"initialOd\":0.1200,"
                + "\"initialPh\":7.200}";
        mockMvc.perform(post("/api/algae/batches")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .header("X-Request-No", "REQ-BATCH-" + stamp)
                        .header("X-Operator", "operator-a")
                        .header("X-Biz-Tz", "Asia/Shanghai")
                        .contentType(MediaType.APPLICATION_JSON).content(createBatchBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.requestNo").value("REQ-BATCH-" + stamp))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.version").value(0));

        // 罐位应已被占用。
        mockMvc.perform(get("/api/algae/reactors/" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.status").value("OCCUPIED"));

        // ---- 2. 复合业务键唯一：重复建立被拒 ----
        mockMvc.perform(post("/api/algae/batches")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(createBatchBody))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BIZ_KEY_DUPLICATED"));

        // ---- 3. CREATED 状态不能补液 ----
        mockMvc.perform(post("/api/algae/feeds")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content(feedBody(
                                "FEED-EARLY", batchNo, reactorCode, feedDate, 100)))
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS"));

        // ---- 4. 非法状态流转：CREATED 不能直接 COMPLETED ----
        mockMvc.perform(post("/api/algae/batches/" + batchNo + "/transition?reactorCode="
                        + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetStatus\":\"COMPLETED\"}"))
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS"));

        // ---- 5. CREATED -> RUNNING ----
        mockMvc.perform(post("/api/algae/batches/" + batchNo + "/transition?reactorCode="
                        + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .header("X-Operator", "operator-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetStatus\":\"RUNNING\"}"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.version").value(1));

        // ---- 6. 5 个并发补液写入请求 ----
        int concurrency = 5;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        for (int i = 0; i < concurrency; i++) {
            final int idx = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    MvcResult result = mockMvc.perform(post("/api/algae/feeds")
                                    .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                                    .header("X-Request-No", "REQ-FEED-" + stamp + "-" + idx)
                                    .header("X-Operator", "operator-" + idx)
                                    .header("X-Biz-Tz", "Asia/Shanghai")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(feedBody("FEED-" + stamp + "-" + idx,
                                            batchNo, reactorCode, feedDate, 100)))
                            .andReturn();
                    JsonNode node = objectMapper.readTree(result.getResponse().getContentAsByteArray());
                    if (node.path("success").asBoolean()) {
                        okCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    failCount.incrementAndGet();
                    throw new RuntimeException(ex);
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发请求未在时限内完成");
        assertEquals(concurrency, okCount.get(), "5 路并发补液应全部成功（乐观锁重试兜底）");
        assertEquals(0, failCount.get());

        // 无丢失更新：初始 100 + 5*100 = 600；版本至少 1（启动）+5（补液）= 6。
        MvcResult detailResult = mockMvc.perform(get("/api/algae/batches/" + batchNo
                        + "/detail?reactorCode=" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.feeds.length()").value(5))
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsByteArray());
        assertEquals(600, detail.path("data").path("batch").path("currentVolumeMl").asInt());
        int batchVersion = detail.path("data").path("batch").path("version").asInt();
        assertTrue(batchVersion >= 6, "5 次补液后批次版本应 >= 6，实际: " + batchVersion);
        assertEquals(7.5, detail.path("data").path("batch").path("latestPh").asDouble(), 0.0001);

        // ---- 7. 补液超容量：事务整体回滚，事件不留存、体积不变 ----
        mockMvc.perform(post("/api/algae/feeds")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(feedBody("FEED-OVER-" + stamp, batchNo, reactorCode,
                                feedDate, 500)))
                .andExpect(jsonPath("$.code").value("VOLUME_EXCEEDED"));
        MvcResult afterOver = mockMvc.perform(get("/api/algae/batches/" + batchNo
                        + "/detail?reactorCode=" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.feeds.length()").value(5))
                .andReturn();
        assertEquals(600, objectMapper.readTree(afterOver.getResponse().getContentAsByteArray())
                .path("data").path("batch").path("currentVolumeMl").asInt());

        // ---- 8. 采样留存 pH/OD 快照，扣减采样体积 ----
        mockMvc.perform(post("/api/algae/samplings")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .header("X-Request-No", "REQ-SAMPLE-" + stamp)
                        .header("X-Operator", "qc-1")
                        .header("X-Biz-Tz", "Asia/Shanghai")
                        .contentType(MediaType.APPLICATION_JSON).content("{"
                                + "\"samplingNo\":\"SMP-" + stamp + "\","
                                + "\"batchNo\":\"" + batchNo + "\","
                                + "\"reactorCode\":\"" + reactorCode + "\","
                                + "\"bizDate\":\"" + feedDate.plusDays(1) + "\","
                                + "\"sampleVolumeMl\":50,"
                                + "\"ph\":7.600,\"od\":0.8800,"
                                + "\"remark\":\"例行采样\"}"))
                .andExpect(jsonPath("$.success").value(true));
        MvcResult afterSample = mockMvc.perform(get("/api/algae/batches/" + batchNo
                        + "/detail?reactorCode=" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.samplings.length()").value(1))
                .andReturn();
        JsonNode sampleNode = objectMapper.readTree(
                afterSample.getResponse().getContentAsByteArray());
        assertEquals(550, sampleNode.path("data").path("batch")
                .path("currentVolumeMl").asInt());
        assertEquals(0.88, sampleNode.path("data").path("batch")
                .path("latestOd").asDouble(), 0.0001);

        // 采样体积超出当前体积被拒。
        mockMvc.perform(post("/api/algae/samplings")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{"
                                + "\"samplingNo\":\"SMP-BAD\","
                                + "\"batchNo\":\"" + batchNo + "\","
                                + "\"reactorCode\":\"" + reactorCode + "\","
                                + "\"bizDate\":\"" + feedDate.plusDays(1) + "\","
                                + "\"sampleVolumeMl\":99999,"
                                + "\"ph\":7.6,\"od\":0.9}"))
                .andExpect(jsonPath("$.code").value("VOLUME_EXCEEDED"));

        // ---- 9. 采收（默认落账），业务日期 3 ----
        mockMvc.perform(post("/api/algae/harvests")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .header("X-Request-No", "REQ-HARVEST-" + stamp)
                        .header("X-Operator", "harvester")
                        .header("X-Biz-Tz", "Asia/Shanghai")
                        .contentType(MediaType.APPLICATION_JSON).content("{"
                                + "\"harvestNo\":\"HV-" + stamp + "\","
                                + "\"batchNo\":\"" + batchNo + "\","
                                + "\"reactorCode\":\"" + reactorCode + "\","
                                + "\"bizDate\":\"" + harvestDate + "\","
                                + "\"harvestVolumeMl\":400,"
                                + "\"ph\":8.100,\"od\":1.2500}"))
                .andExpect(jsonPath("$.data.posted").value(true));
        MvcResult afterHarvest = mockMvc.perform(get("/api/algae/batches/" + batchNo
                        + "/detail?reactorCode=" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.harvests.length()").value(1))
                .andReturn();
        assertEquals(150, objectMapper.readTree(afterHarvest.getResponse().getContentAsByteArray())
                .path("data").path("batch").path("currentVolumeMl").asInt());

        // ---- 10. 已落账采收记录不能删除 ----
        mockMvc.perform(delete("/api/algae/harvests/HV-" + stamp)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));

        // 未发生的采收记录删除返回 NOT_FOUND。
        mockMvc.perform(delete("/api/algae/harvests/NOT-EXIST")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // ---- 11. RUNNING -> COMPLETED：补液统一落账并释放罐位 ----
        mockMvc.perform(post("/api/algae/batches/" + batchNo + "/transition?reactorCode="
                        + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetStatus\":\"COMPLETED\"}"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mockMvc.perform(get("/api/algae/reactors/" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));

        // 完成后补液已落账，不能删除。
        mockMvc.perform(delete("/api/algae/feeds/FEED-" + stamp + "-0")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));

        // COMPLETED 不能再登记采收。
        mockMvc.perform(post("/api/algae/harvests")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{"
                                + "\"harvestNo\":\"HV-LATE\","
                                + "\"batchNo\":\"" + batchNo + "\","
                                + "\"reactorCode\":\"" + reactorCode + "\","
                                + "\"bizDate\":\"" + harvestDate + "\","
                                + "\"harvestVolumeMl\":10,"
                                + "\"ph\":8.1,\"od\":1.2}"))
                .andExpect(jsonPath("$.code").value("ILLEGAL_STATUS"));

        // ---- 12. COMPLETED -> ACCEPTED，已验收批次不能删除 ----
        mockMvc.perform(post("/api/algae/batches/" + batchNo + "/transition?reactorCode="
                        + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetStatus\":\"ACCEPTED\"}"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        mockMvc.perform(delete("/api/algae/batches/" + batchNo + "?reactorCode=" + reactorCode)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));

        // ---- 13. 审计链：请求号/操作者/时区/业务日期/版本快照同事务落库 ----
        MvcResult auditResult = mockMvc.perform(get("/api/algae/audit/requests/REQ-HARVEST-"
                        + stamp)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].operatorCode").value("harvester"))
                .andExpect(jsonPath("$.data[0].bizTimezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.data[0].bizObject").value("HARVEST"))
                .andExpect(jsonPath("$.data[0].bizDate").value(harvestDate.toString()))
                .andReturn();
        JsonNode audit = objectMapper.readTree(auditResult.getResponse().getContentAsByteArray());
        String snapshot = audit.path("data").get(0).path("versionSnapshot").asText();
        assertTrue(snapshot.contains("\"versionBefore\""), "版本快照应含 versionBefore");
        assertTrue(snapshot.contains("\"phSnapshot\""), "采收快照应留存 pH");
        assertTrue(snapshot.contains("\"odSnapshot\""), "采收快照应留存光密度");

        // 建立批次的审计留存了 3 要素中的接种业务日期与初始快照。
        mockMvc.perform(get("/api/algae/audit/requests/REQ-BATCH-" + stamp)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data[0].operatorCode").value("operator-a"))
                .andExpect(jsonPath("$.data[0].bizDate").value(inoculationDate.toString()));
    }

    @Test
    void unauthenticatedRequest_isRejected() throws Exception {
        mockMvc.perform(get("/api/algae/reactors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptedStrain_cannotBeDeleted() throws Exception {
        String strainNo = "ST-" + stamp();
        mockMvc.perform(post("/api/algae/strains/" + strainNo + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.data.accepted").value(true));
        mockMvc.perform(delete("/api/algae/strains/" + strainNo)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH))
                .andExpect(jsonPath("$.code").value("RECORD_LOCKED"));
    }

    private MvcResult postJson(String url, String body, String requestNo, String operator)
            throws Exception {
        return mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .header("X-Request-No", requestNo)
                        .header("X-Operator", operator)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
    }

    private String feedBody(String feedNo, String batchNo, String reactorCode,
                            LocalDate bizDate, double volume) {
        return "{"
                + "\"feedNo\":\"" + feedNo + "\","
                + "\"batchNo\":\"" + batchNo + "\","
                + "\"reactorCode\":\"" + reactorCode + "\","
                + "\"bizDate\":\"" + bizDate + "\","
                + "\"medium\":\"BG-11 培养基\","
                + "\"feedVolumeMl\":" + volume + ","
                + "\"ph\":7.500,\"od\":0.5000}";
    }
}
