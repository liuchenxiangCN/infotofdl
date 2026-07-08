# Informatica XML 到 FDL 迁移覆盖审计报告

审计日期：2026-07-02

源码目录：`/home/liufeng/code/bainianrenshou/infotofdl`

样例目录：`/mnt/d/项目文档/2-项目/百年人寿`

目标口径：构建通用 Informatica XML 到 FDL 的功能转换器，按 XML 中记录的任务语义还原 FDL 任务结构，不按业务含义确认字段、接口或变量。

已确认：Informatica 调度配置不在当前范围内。

已确认：用户不会审计代码，只会通过 FDL 用户界面验证迁移结果。后续验收材料必须转化为 UI 可观察项。

## 结论

当前 `0.3.0` 插件可以作为转换器基础，但尚未覆盖全部 Informatica XML 功能语义到 FDL 结构的翻译。

可覆盖的部分主要是：Workflow/Session/Worklet 结构识别、Source Qualifier SQL、Pre SQL、Post SQL、Truncate、目标表、数据源连接名、基础连线、部分参数、一个 Java Transformation 到 API 节点的初步转换。

主要功能翻译缺口是：

1. `Decision` 条件节点和 `$判定.Condition = true` 这类运行时条件输出需要翻译为 FDL 条件结构或 adapter binding。
2. `Expression`、`Filter`、`Lookup Procedure` 等 Informatica 转换逻辑要继续按 XML 表达式、条件、Lookup cache/multiple match policy 生成 FDL SQL/dataflow。
3. Java SOAP 调用已能解析 URL、body、请求字段、输出字段和 HTTP status 映射，剩余是把行级条件和状态映射生成到 FDL 原生 API 或插件 adapter。
4. ODBC/JDBC_ODBC 连接应保留连接名、原始类型和 SQL，并通过 FDL datasourceType 映射或通用 JDBC adapter 生成，不应默认当作 MySQL。
5. 字段映射要基于 XML `CONNECTOR`、端口表达式、SQL alias、默认值和 unconnected lookup 语义生成，不能只依赖 FDL 运行时刷新后的同名匹配。
6. 日期参数、MappingVariable、WorkflowVariable 和 `${Param}` 引用要按 Informatica 作用域、默认值、持久化属性和引用位置翻译。

因此当前状态应判定为：**XML 语义已大量解析，剩余是 FDL 功能翻译和 FDL 结构调研清单**。

下一阶段应先补“迁移验证 harness + 可重复打包”，再按功能语义优先级实现翻译补齐。

## 样例总览

| 文件 | Folder | Workflow | Worklet | Session | Mapping | MappingVariable | WorkflowVariable | 条件连线 | Java/API |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `WF_HNC.XML` | PRIP | 1 | 3 | 1 | 1 | 3 | 81 | 0 | 0 |
| `WF_PRIP_Stage.XML` | PRIP | 1 | 12 | 199 | 199 | 476 | 2744 | 1 | 1 |
| `auditdb.xml` | AUDITDB | 10 | 0 | 22 | 22 | 0 | 348 | 0 | 0 |
| `cc_lis_20260521.xml` | cc_lis | 2 | 0 | 4 | 4 | 0 | 64 | 0 | 0 |

## Informatica 能力覆盖

| 能力 | 样例中出现 | 当前插件状态 | 功能翻译状态 |
|---|---:|---|---|
| 多 Workflow | `auditdb.xml` 10 个，`cc_lis_20260521.xml` 2 个 | `parseAllWorkflows` 已支持多任务生成 | 已翻译 |
| Worklet | `WF_HNC.XML` 3 个，`WF_PRIP_Stage.XML` 12 个 | 已展开为 session groups | 已翻译，仍需覆盖复杂运行条件 |
| Session 数据同步 | 226 个 | 已生成 FDL 数据同步节点 | 已翻译基础结构 |
| Source Qualifier SQL | 226 个 | 已提取 SQL 或生成默认 SQL | 已翻译 |
| Pre SQL/Post SQL | 多数 session 存在 | 已生成 SQL 节点 | 已翻译基础结构 |
| Truncate | `WF_PRIP_Stage.XML` 5 个，`auditdb.xml` 1 个 | 已生成 truncate SQL 节点 | 已翻译，仍需归入完整写入策略 |
| MappingVariable | 479 个 | 已转任务参数，日期参数另存全局参数 | 待按作用域/默认值/引用位置翻译 |
| WorkflowVariable | 3237 个 | 已部分转任务参数 | 待按作用域/默认值/持久化属性翻译 |
| Decision | `WF_PRIP_Stage.XML` 1 个 | 已结构化解析，严格 success-AND 子集可执行 | 其余运行时状态绑定待翻译 |
| Conditional Link | `WF_PRIP_Stage.XML` 1 条 | 已保留条件语义并分类 | 复杂条件绑定待翻译 |
| Expression | `WF_HNC.XML` 1 个，`WF_PRIP_Stage.XML` 153 个，`auditdb.xml` 11 个 | 透传/部分计算可投影，复杂依赖保留语义 | 待继续生成 SQL/FDL 表达式 |
| Filter | `auditdb.xml` 5 个 | 安全 Filter 可进入 SparkSqlNode WHERE | 依赖待翻译 Lookup 的 Filter 随上游生成 |
| Lookup Procedure | `auditdb.xml` 6 个 | 等值 Lookup 可进入 FDL dataflow | 非等值和未连接输入待翻译 |
| Java Transformation | `WF_PRIP_Stage.XML` 1 个 | API adapter contract 已结构化 | 行级条件和 HTTP status binding 待生成 |

## 文件级功能翻译状态

### `WF_HNC.XML`

结构：1 个 Workflow，3 个 Worklet，1 个可复用 Session。

连接：`lis_uat152` 读，`prip_155` 写。

功能翻译重点：

- 3 个 Worklet 都引用同一个 Session `s_M_PRIP_Stage_LCCont`，需要保持 Informatica 复用 Session 的运行实例语义。
- 有 1 个 Expression，105 个字段表达式。多数表达式可由同名透传解释，仍应按 XML 表达式逐字段翻译计算、类型转换、默认值或条件逻辑。

### `WF_PRIP_Stage.XML`

结构：1 个 Workflow，12 个 Worklet，199 个 Session，1 个 Decision，1 个 Java Transformation。

连接：主要是 `lis_uat152` 读、`prip_155` 写，也包含 `qd_214_uat_r`、`crm133`、`xqgl119`、`east`。

功能翻译重点：

- `Decision` 节点 `判定` 有一条条件出边：`判定 -> s_M_PRIP_ExtractData`，条件为 `$判定.Condition = true`。当前插件会把 `判定` 当成中转节点折叠，条件丢失。
- 153 个 Expression，5817 个 Expression 字段，不能默认认为全部可透传。
- Java Transformation `Java` 属于 mapping `M_PRIP_ExtractData`，session 是 `s_M_PRIP_ExtractData`。
- Java SOAP URL 可提取：`http://20.12.2.153:8403/services/ExtractionPripDataService?wsdl`。
- SOAP XML body 可提取，但 `SOAPAction` 在代码片段里是变量 `intfMethod`，当前插件无法解析成确定 header。
- Post/Pre SQL 数量很大，执行顺序和失败策略要与 FDL 线条条件一起验证。

### `auditdb.xml`

结构：10 个 Workflow，22 个 Session。

连接：读源包括 `agentchannel`、`training_uat`、`aml_171_uat_r`、`cem_214_uat_r`、`lis_uat152`、`qd_214_uat_r`；写入 `auditdb_99_uat_rw_sjods_user`。

功能翻译重点：

- 5 个 Filter，当前未转换。
- 6 个 Lookup Procedure，当前未转换。
- LOOKUPEXTENSION 连接出现 6 次，当前迁移节点不会单独建 lookup 读取逻辑。
- `agentchannel`、`training_uat` 是 ODBC/JDBC_ODBC，插件应保留连接名和 SQL，并通过 datasourceType 映射或通用 JDBC adapter 生成。

### `cc_lis_20260521.xml`

结构：2 个 Workflow，4 个 Session。

连接：`cc_133_uat_rw` 与 `lis_190_uat_newcrm_rw` 互为读写。

功能翻译重点：

- 结构最简单，主要是数据源、SQL、目标表、字段映射验证。
- 适合作为第一条端到端 smoke test。

## 本地 FDL 需要准备的数据连接名称

这些名称必须和 XML 中 `CONNECTIONNAME` 完全一致，否则当前插件生成节点时会找不到对应连接。

当前状态：用户已在本地 FDL 手动新建好这些名称的数据连接。

| 连接名 | XML 类型 | 角色 | 涉及文件 | 建议本地 FDL 类型 |
|---|---|---|---|---|
| `prip_155` | Oracle | 读/写，主要写 | `WF_HNC.XML`, `WF_PRIP_Stage.XML` | Oracle |
| `lis_uat152` | Oracle | 主要读 | `WF_HNC.XML`, `WF_PRIP_Stage.XML`, `auditdb.xml` | Oracle |
| `qd_214_uat_r` | Oracle | 读/Lookup | `WF_PRIP_Stage.XML`, `auditdb.xml` | Oracle |
| `crm133` | Oracle | 读 | `WF_PRIP_Stage.XML` | Oracle |
| `xqgl119` | Oracle | 读 | `WF_PRIP_Stage.XML` | Oracle |
| `east` | Oracle | 读 | `WF_PRIP_Stage.XML` | Oracle |
| `auditdb_99_uat_rw_sjods_user` | Oracle | 写 | `auditdb.xml` | Oracle |
| `aml_171_uat_r` | Oracle | 读/Lookup | `auditdb.xml` | Oracle |
| `cem_214_uat_r` | Oracle | 读 | `auditdb.xml` | Oracle |
| `cc_133_uat_rw` | Oracle | 读/写 | `cc_lis_20260521.xml` | Oracle |
| `lis_190_uat_newcrm_rw` | Oracle | 读/写 | `cc_lis_20260521.xml` | Oracle |
| `agentchannel` | ODBC | 读 | `auditdb.xml` | FDL datasourceType 映射或通用 JDBC adapter |
| `training_uat` | ODBC | 读 | `auditdb.xml` | FDL datasourceType 映射或通用 JDBC adapter |

本地测试最小连接集建议分三档：

1. Smoke test：只建 `cc_133_uat_rw`、`lis_190_uat_newcrm_rw`，先验证最简单文件。
2. Worklet test：再建 `prip_155`、`lis_uat152`，验证 `WF_HNC.XML`。
3. Full coverage test：补齐上表全部连接，验证 `WF_PRIP_Stage.XML` 和 `auditdb.xml`。

如果只是验证“插件能生成任务并打开”，连接可以指向测试库，但必须有足够权限读取元数据、执行 SQL、读取目标表字段。若要验证运行结果，连接必须指向可代表原 Informatica 连接的数据源和目标库。

## 当前插件需要优先改造的点

### P0：建立可重复构建和离线验证

- 从本地已安装插件 jar 补齐 `jar-contents` 和 `pluginkit` 基线。
- 使用 FDL 自带 JDK8 构建，不依赖 WSL 全局 Java。
- 建一个离线解析报告工具，输出每个 XML 生成的任务、节点、连线、参数、连接、目标表、API 配置。

### P1：保留 Workflow 条件语义

- 扩展 `Link`，保留 `WORKFLOWLINK/@CONDITION`。
- 处理 `Decision` 节点，不应静默折叠条件。
- 如果 FDL 支持条件线，映射到 FDL 条件线；如果不支持，至少生成显式条件/参数判断节点，保证运行语义可见。

### P1：补 Java SOAP API 迁移

- 解析 `SOAPAction` 变量来源，例如 `intfMethod`。
- 明确 Content-Type：当前默认 `application/soap+xml;charset=utf-8`，但源代码里 Content-type 设置被注释。
- 解析响应字段，不应只生成一个 `status` 字段。
- 确认 API 节点写入目标表的字段映射。

### P1：处理非 Source Qualifier 转换

- Expression：先识别纯字段透传和真实计算表达式。纯透传可以直接字段映射，真实计算需要转 SQL 或 FDL 表达式。
- Filter：应转成 SQL WHERE 或独立过滤节点。
- Lookup Procedure：应转成 SQL join、独立数据同步/SQL 节点，或 FDL 可表达的 lookup 逻辑。

### P2：参数策略

- 保留 MappingVariable/WorkflowVariable 的作用域和默认值。
- 日期参数不要只靠名称启发式写 `sysdate`/`sysdate-1`。
- 确认 `${Param}` 语法在 FDL SQL/API body 中都可执行。

### P2：连接类型映射

- ODBC/JDBC_ODBC 不应默认等同 MySQL；应保留连接名、SQL 和原始类型，并生成 FDL datasourceType 映射或通用 JDBC adapter。
- 需要支持连接名映射配置，以便 FDL 连接名与 Informatica 连接名不一致时仍能自动迁移。

## 建议执行顺序

1. 先用 `cc_lis_20260521.xml` 做端到端 smoke test。
2. 再用 `WF_HNC.XML` 验证 Worklet + 可复用 Session。
3. 实现 Decision/条件线后，再验证 `WF_PRIP_Stage.XML`。
4. 实现 Filter/Lookup 后，再验证 `auditdb.xml`。
5. 最后做全量导入和执行对账。
