# EVOPS 基础工作区 · 微藻生物反应器培养与采收闭环

在初始骨架（统一返回、异常处理、MyBatis-Plus、H2、Shiro）上实现了微藻培养闭环：
**藻种批次 → 培养罐（反应器柜位）→ 培养批次 → 补液/采样/采收 → 完成/验收**。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2、Shiro（HTTP Basic）。

## 业务对象（7 张表，见 `src/main/resources/schema.sql`）

| 表 | 业务键（唯一） | 关键规则 |
|---|---|---|
| `t_algae_strain` 藻种批次 | `strain_no` | 验收（accepted）后不可删除 |
| `t_algae_reactor` 培养罐 | `reactor_code` | 有在制批次时不可删除；建批占用、终态释放 |
| `t_algae_culture_batch` 培养批次 | **复合键 `(batch_no, reactor_code)`** | 状态机 + 乐观锁版本 |
| `t_algae_feed_event` 补液 | `feed_no` | 完成培养时统一落账，落账后不可删除 |
| `t_algae_sampling_event` 采样 | `sampling_no` | pH/OD 快照必录 |
| `t_algae_harvest_record` 采收 | `harvest_no` | 创建即落账，落账后不可删除 |
| `t_algae_audit_log` 审计 | — | 请求号/操作者/业务时区/业务日期/版本快照 |

状态机：`CREATED --RUNNING--> RUNNING --COMPLETED--> COMPLETED --ACCEPTED--> ACCEPTED`；
`CREATED/RUNNING --TERMINATED--> TERMINATED`。非法流转返回 `ILLEGAL_STATUS`。

三个业务日期：接种日期（建批）、补液/采样业务日期、采收日期（均带业务时区）。

## 同事务跨表写入

补液/采样/采收在**一个事务**内完成：写业务事件 → 用带版本与容量上下限守卫的单条
SQL 推进批次体积与 pH/OD 快照（`CultureBatchMapper.applyVolumeChange`）→ 写审计。
任一失败整体回滚（超灌/超取不会留下无主事件）。5 路并发写入由乐观锁 +
外层退避重试（`CultureBatchService`）保证无丢失更新。

## REST 接口（Shiro Basic：`bootstrap/bootstrap`）

请求头：`X-Request-No`（请求号，缺省自动生成）、`X-Operator`（操作者）、`X-Biz-Tz`（业务时区，默认 Asia/Shanghai）。

- `POST/GET /api/algae/strains`，`POST /api/algae/strains/{no}/accept`，`DELETE /api/algae/strains/{no}`
- `POST/GET /api/algae/reactors`，`GET/DELETE /api/algae/reactors/{code}`
- `POST/GET /api/algae/batches`，`POST /api/algae/batches/{batchNo}/transition?reactorCode=`，
  `GET /api/algae/batches/{batchNo}/detail?reactorCode=`（关联藻种/罐/补液/采样/采收），
  `DELETE /api/algae/batches/{batchNo}?reactorCode=`
- `POST /api/algae/feeds`、`POST /api/algae/samplings`、`POST /api/algae/harvests`
- `DELETE /api/algae/feeds/{feedNo}`、`DELETE /api/algae/samplings/{no}`、`DELETE /api/algae/harvests/{no}`
- `GET /api/algae/audit/requests/{requestNo}`（凭请求号回溯审计链与版本快照）

统一返回：`{ success, code, message, data, requestNo }`。错误码见 `BizErrorCode`
（`BIZ_KEY_DUPLICATED` / `ILLEGAL_STATUS` / `RECORD_LOCKED` / `VOLUME_EXCEEDED` /
`CONCURRENT_UPDATE` / `NOT_FOUND` / `BAD_REQUEST`）。

## 运行与测试

```bash
mvn -q -DskipTests compile      # 编译
mvn test                         # 集成测试（含 5 并发补液、闭环、删除保护、审计）
mvn spring-boot:run              # 启动后访问 http://localhost:8080/api/health
```

示例：

```bash
curl -u bootstrap:bootstrap -X POST http://localhost:8080/api/algae/batches \
  -H 'X-Request-No: REQ-001' -H 'X-Operator: op1' -H 'X-Biz-Tz: Asia/Shanghai' \
  -H 'Content-Type: application/json' \
  -d '{"batchNo":"B-001","reactorCode":"RC-001","strainNo":"ST-001",
       "initialVolumeMl":500,"inoculationDate":"2026-09-01",
       "initialOd":0.12,"initialPh":7.2}'
```

测试使用独立内存库 profile（`src/test/resources/application-test.yml`），不污染本地文件数据。
