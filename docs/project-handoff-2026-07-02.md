# 项目交接文档：Informatica XML 到 FDL 插件迁移

日期：2026-07-02

## 背景

用户要修改一款帆软 FDL 插件，源码仓库为：

`https://gitee.com/gongezh/infotofdl`

插件主要功能是把 Informatica PowerCenter 导出的 XML 任务迁移到 FineDataLink，用 FDL 的任务流、数据同步、SQL、API 等能力实现原任务。

目标口径：本项目是通用 Informatica XML 到 FDL 功能转换器，按 XML 中记录的任务语义生成 FDL 任务结构，不按业务含义确认字段、接口或变量。

已确认：Informatica 的调度配置不在当前范围内，本阶段只处理任务结构、节点、SQL/API、参数、连线、字段映射和运行语义。

已确认：用户不会审计代码，只会通过本地 FDL 用户界面验证结果。后续每个开发阶段都需要提供可安装包、UI 验证步骤、预期现象和回退方式。

## 当前仓库状态

源码已克隆到：

`/home/liufeng/code/bainianrenshou/infotofdl`

当前基线提交：

`ff54847f49d59d6cf0e8adb6e368d5c370b3d9fb`

当前已新增审计报告：

`docs/migration-coverage-audit-2026-07-02.md`

当前已新增构建与 UI 验证说明：

`docs/build-and-ui-smoke-test.md`

当前已新增下一对话提示词：

`docs/next-session-prompt-field-lineage-audit-2026-07-02.md`

当前已新增字段级 lineage 离线审计：

- `scripts/audit_infa_field_lineage.py`
- `scripts/audit_infa_api_calls.py`
- `docs/lineage-audit/README.md`
- `docs/lineage-audit/*.csv`
- `docs/lineage-audit/*.md`

当前已接入插件字段级映射 lineage：

- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FieldLineageInfo.java`
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java`
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/GroupSQLInfo.java`
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/SyncTaskNodeBean.java`
- `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`

当前已接入 G1 Workflow / Worklet 条件控制流保留：

- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/Link.java`：保留 `WORKFLOWLINK/@CONDITION` 原文。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ConditionNodeBean.java`：生成 FDL `CONDITION_NODE`，把 Informatica 条件写入条件项和节点备注。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java`：Decision 不再作为未知 connector 静默折叠；未知中转节点压缩时会传递条件；普通有条件连线会补一个可见条件节点。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/parse/DataSyncParseNodeBean.java` / `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/service/FDLService.java`：生成 FDL 连线时把条件节点的下游分支写入 `conditionItems`，同时在线 value 中保留 `infaCondition`/`conditionString`。
- `scripts/audit_infa_workflow_conditions.py`：离线审计 Workflow/Worklet Decision 和条件线。

当前已接入 G2 Java / SOAP / API 调用可见迁移：

- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/api/ApiNodeFromInfoBean.java`：保存外部调用 URL、方法、headers、SOAPAction 来源、请求字段、输出字段到目标字段映射和限制说明。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/ApiSyncTaskNodeBean.java`：生成 FDL 原生 API 读节点 + DB 写节点，写入请求 body/headers/响应字段，并把 API 输出字段映射到目标表字段。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java`：通用识别 Java Transformation / Web Service / SOAP / HTTP / API 类外部调用，从 metadata、端口和 CONNECTOR 中提取调用和字段语义。
- `scripts/audit_infa_api_calls.py`：只读审计 XML 中外部调用，输出节点、动作、SOAPAction、请求字段、响应字段和限制，敏感值脱敏。

当前已接入 G3 Expression 转换可见迁移：

- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/ExpressionFieldInfo.java`：保存 Expression 原文、类型、依赖字段、目标字段、SQL 表达式和限制。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FieldLineageInfo.java`：保存目标字段到 Expression 明细的关系，并生成 UI 备注。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java`：解析 Expression 透传/改名/常量/SQL 表达式/条件表达式/`ERROR(...)`/未知函数；普通 DB 同步在依赖均来自 Source Qualifier/Source Definition 且表达式可转换时追加 SQL 投影；Java/API 或不确定依赖不下推，只显式写限制。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/SyncTaskNodeBean.java`：把 Expression 备注写入同步组件、reader、writer 和目标字段注释。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/VirtualNodeBean.java`：支持备注，用于生成 `*_expression` 可视节点。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/api/ApiNodeFromInfoBean.java`：API 数据流备注展示 downstream Expression 和待翻译限制。
- `scripts/audit_infa_expressions.py`：只读审计 XML 中 Expression 字段，输出 `docs/lineage-audit/expression-audit/`。

当前已接入 G4 Filter / Lookup Procedure 可见迁移：

- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FilterFieldInfo.java`：保存 Filter 条件、依赖字段、输出字段、目标字段、SQL WHERE 候选和限制。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/LookupFieldInfo.java`：保存 Lookup Procedure 输入字段、输出字段、Lookup SQL/表、连接、条件、目标字段、SQL join 候选和限制。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FieldLineageInfo.java`：统一聚合 Expression、Filter、Lookup 可见语义。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java`：解析 Filter / Lookup Procedure 元数据；安全 Filter 会追加外层 SQL WHERE；安全 Lookup 候选会追加外层 LEFT JOIN；尚未覆盖的语义保留为插件待翻译状态。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/SyncTaskNodeBean.java`：同步组件、reader、writer 和目标字段注释显示 Filter / Lookup 明细。
- `scripts/audit_infa_filters_lookups.py`：只读审计 XML 中 Filter / Lookup Procedure，输出条件、配置、输入/输出/目标字段、转换状态和限制，并脱敏敏感值。

当前已接入 G5 连接类型识别与 ODBC/JDBC_ODBC 可见迁移策略：

- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/ConnectionMappingInfo.java`：保存连接名、原始类型/子类型、用途、Session/Mapping、FDL datasourceType、映射状态、依据和限制。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/util/xml/DataSyncParser.java`：`parseConnectionReferences` 不再把 `ODBC` / `JDBC_ODBC` 静默改成 `MySQL`；Oracle/MySQL/SQL Server/PostgreSQL 走安全映射，ODBC/JDBC_ODBC 归为 FDL datasourceType 映射或通用 JDBC adapter 待实现。
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/FieldLineageInfo.java`：连接信息进入 `*_infa_connections` / `*_infa_semantics` 可视节点、同步组件、reader 和 writer 备注。
- `scripts/audit_infa_connections.py`：只读审计四个 XML 的 `SESSIONEXTENSION/CONNECTIONREFERENCE`，输出连接用途、原始类型、FDL 映射类型、状态和限制，并脱敏敏感值。

当前已接入 G6 FDL 数据流内 Lookup / Filter 可执行转换：

- 已确认 FDL 4.2 运行库存在 `FilterDataNode`、`JoinDataNode`、`SparkSqlNode`、`AddCalculateColumnNode` 等转换节点；第一阶段选择接口最稳定的 `SparkSqlNode` 作为半原生 SQL transform。
- 新增 `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/TransformSyncTaskNodeBean.java`：生成 `DATA_FLOW`，内部结构为 `DBReadNode(base SQL) + DBReadNode(lookup SQL) -> SparkSqlNode(LEFT JOIN / WHERE) -> DBWriteNode`。
- 新增 `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/bean/parse/core/fdl/db/DataFlowTransformInfo.java`：保存 lookup read 节点、Spark SQL、输出字段和 UI 备注。
- `DataSyncParser.java` 现在会把纯透传/改名 Expression 向上追踪到 Source Qualifier 字段；安全等值 Lookup 不再因 `PLAN3_IN <= EXPTRANS.PLAN3` 这类纯透传输入而误判为不可执行。
- `FilterFieldInfo.java` / `LookupFieldInfo.java` 增加 `FDL dataflow ... candidate/applied` 状态，UI 备注能区分 FDL 可执行转换、旧 SQL rewrite 候选和 visible-only 限制。
- `scripts/audit_infa_filters_lookups.py` 已扩展输出 `FDL_DATAFLOW_EXECUTABLE`、`SQL_REWRITE_CANDIDATE`、`XML_SEMANTICS_PARSED_TRANSLATION_PENDING`、`FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED`。

当前已接入 G7 通用 Lookup / Filter 执行语义增强：

- `DataSyncParser.java` 的 Lookup 条件解析从 equality-only 字符串判断改为通用谓词解析，支持 `=`、`!=`、`<>` 和多条件 `AND`。
- Lookup join 谓词只有在每个条件都能配对一个 lookup 读字段和一个可追溯到 base reader 的输入字段时才生成；输入 unresolved 时保留明确限制原因。
- 非等值 Lookup 不直接执行化：join 谓词可解析后先生成 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY` 单行语义计划；在 Informatica lookup first-match/cache/multiple match policy 的行选择顺序证明前，不标为可执行等价。
- Source Qualifier SQL alias / 直接 SQL read alias 解析已加入 lineage，纯 Expression 透传/改名链路仍可继续追溯到 reader 字段。
- 下游 Filter 只有在上游 Lookup 已执行化且 Filter 条件可安全转换时才进入同一个 `SparkSqlNode WHERE`；上游非等值 Lookup 已有单行计划时，Filter 标记为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY` 并在 rn=1 后执行。
- `scripts/audit_infa_filters_lookups.py` 对非等值 Lookup 输出 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY` 状态，并输出 join predicate、single-row SparkSQL 草案、unresolved 输入原因和行选择顺序待证明原因。
- 新增 `scripts/check_no_sample_hardcoding.py`，扫描 `plugin-src/src` 生产代码，防止样例 XML / Mapping / Session / 字段名硬编码进入插件逻辑。

当前已接入 G8 Workflow condition / Decision / external call 执行语义增强：

- 已审计 FDL 4.2 运行库：任务流可用 `ConditionComponent`、`DPConditionItem`、`AndOrConditionEntity`、`CompareConditionEntity`、`SelfConditionEntity` 承载简单条件；未发现稳定的任务流 Branch/Gateway node entity。离线 dataflow 仍有 `ApiReadNode`、`SparkSqlNode`、`FilterDataNode`、`JoinDataNode`。
- `ConditionNodeBean` 支持解析简单布尔、比较、`AND`/`OR` 复合条件，输出 `EXECUTABLE`、`FDL_GENERATION_RESEARCH_PENDING`、`XML_SEMANTICS_PARSED_TRANSLATION_PENDING`、`FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED` 状态。只有未涉及 Informatica scoped/task runtime 变量的安全条件才写入 FDL condition entity；`.Status`、`.Condition` 等运行时变量保留为 conditionString 和备注并标为 FDL 结构待调研。
- `DataSyncParser.java` 会解析 Decision `TASK` 元数据，把 Decision 表达式、状态和限制写入 FDL 条件节点备注；缺少 FDL runtime binding 的任务状态聚合时生成 FDL adapter 待实现说明。
- `ApiNodeFromInfoBean` 增加 `FDL_TRANSLATION_READY`、`FDL_GENERATION_RESEARCH_PENDING`、`FDL_ADAPTER_REQUIRED`、`XML_SEMANTICS_PARSED_TRANSLATION_PENDING` 状态、protocol、Java class/method、adapter metadata 和 required config 字段。只有 endpoint/protocol/request/response mapping 足够时才生成 FDL API 节点；元数据不完整时生成可见 adapter 占位节点。
- `scripts/audit_infa_workflow_conditions.py`、`scripts/audit_infa_api_calls.py`、`scripts/audit_infa_expressions.py` 已按 G8 状态输出限制原因；`scripts/check_no_sample_hardcoding.py` 已扩展 Decision/API 样例标识扫描。

当前已接入 G9 Informatica Decision / task-status runtime semantics 增强：

- 已再次审计 FDL 4.2 当前运行库：公开 API 可见 `ConditionComponent` / `DPConditionItem` / condition entity、`ComponentLine#lineCondition`、WorkRecord/ExecuteTaskStatus/OfflinePipeline record 等状态查询结构；但未发现普通任务流 condition entity 可直接绑定上游组件执行结果、Decision 输出变量或 `$Task.Status` 的可执行分支 API。因此 G9 不伪造 executable Decision 分支。
- `ConditionNodeBean` 现在结构化解析 `$TaskName.Status = succeeded/failed/disabled`、`!=`、`AND`/`OR` 组合，并把 known Informatica status literal 归一为通用状态值；缺少 FDL runtime binding 的谓词分类为 `FDL_GENERATION_RESEARCH_PENDING_TASK_STATUS_CONDITION`，并在 FDL 节点备注中列出 task runtime status source、状态映射和聚合时机等 required bindings。
- `$DecisionName.Condition = true/false` 会被结构化识别为 Decision output binding，分类为 `FDL_GENERATION_RESEARCH_PENDING_DECISION_OUTPUT_BINDING`；FDL 节点备注会列出 Decision expression result storage、runtime boolean output 和 outgoing branch binding 等 required bindings。
- `DataSyncParser.java` 会把上述 parsed predicates/references、classification 和 required adapter bindings 写入 Decision 条件节点描述；`scripts/audit_infa_workflow_conditions.py` 同步输出细分分类、变量引用、谓词、Decision output reference、limitation 和 required bindings。
- `scripts/check_no_sample_hardcoding.py` 已扩展 G9 涉及的 task-status / Decision 样例标识扫描；生产 Java/parser 逻辑仍保持通用，不硬编码样例 XML、Workflow、Worklet、Session、Decision、字段或连接名。

当前已接入 G10 可证明 Decision/status 原生执行子集与 API 状态映射解析：

- `ConditionNodeBean` 增加 FDL line condition 解析：直接条件 `$FromTask.Status = succeeded` 可映射为 `ON_SUCCESS`，`$FromTask.Status = failed` 可映射为 `ON_FAIL`；disabled/running/skipped 等未证明状态仍不执行化。
- `DataSyncParser.java` 会识别严格 success-AND Decision：Decision 表达式全为直接上游 `$Task.Status = succeeded`，所有上游入线为空条件，且 Decision `TREAT_INPUTLINK_AS_AND=YES` 时，使用 FDL `ON_SUCCESS` 入线作为执行门控，并把 `$Decision.Condition = true` 出分支转换为可执行 literal true 条件。
- `FDLService.java` 生成连线时会把支持的 Informatica task-status 条件写成 FDL `lineCondition`，同时保留 `infaCondition`/`conditionString` 元数据。
- `ApiNodeFromInfoBean` / `DataSyncParser.java` / `scripts/audit_infa_api_calls.py` 会从 Java helper 方法解析 HTTP status -> output/default output 映射，并把 row-level invocation condition、HTTP status-code output binding 列为 required adapter config；API 状态按 `FDL_GENERATION_RESEARCH_PENDING` 或 `FDL_ADAPTER_REQUIRED` 暴露 FDL 生成缺口。
- `scripts/audit_infa_workflow_conditions.py` 与插件规则同步：`WF_PRIP_Stage.XML` 现在输出 `EXECUTABLE_TASK_STATUS_CONDITION` 和 `EXECUTABLE_DECISION_OUTPUT_BINDING`；未命中严格模式时输出 FDL runtime binding 或 adapter 待实现状态。

当前已接入 G11 Java/SOAP/API adapter contract 与后续表达式功能翻译：

- `ApiNodeFromInfoBean` 新增 `FDL_ADAPTER_REQUIRED` 状态，并显式记录 row-level invocation condition 字段、HTTP status-code output binding、parsed adapter contract bindings；`FDL_TRANSLATION_READY` 只保留给无需 adapter 的原生可执行 API。
- `DataSyncParser.java` 会通用解析 Java 行级调用条件字段并检查这些字段是否绑定到 API 输入端口；会检查 HTTP status mapping 目标是否为 API 输出端口。`WF_PRIP_Stage.XML` 的 `STATE` 输入与 `OUT_STATE -> STATE` 状态输出绑定均命中，因此 API 变为 `FDL_ADAPTER_REQUIRED`。
- `scripts/audit_infa_api_calls.py` 与插件分类同步，当前输出 `statuses={'FDL_ADAPTER_REQUIRED': 1}`，并列出 `rowInvocationMode=predicate-before-request`、`rowInvocationFields=STATE`、`httpStatusOutputBinding=parsed-from-java-getResponseCode`。
- `scripts/audit_infa_expressions.py` 已重新评估 `EXPTRANS.OUT_ERRORINFO`：上游 API 输出已 parsed adapter contract；当前保留为 `FDL_API_POST_PROJECTION_ADAPTER_REQUIRED`，不再生成 `source_reader -> api_reader` 这类 FDL 禁止的输入节点连线，剩余是实现可执行 API/row-binding adapter。
- `scripts/audit_infa_filters_lookups.py` 对 auditdb 非等值 Lookup 补充缓存与多匹配策略证据；两个非等值 Lookup 已升级为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，已生成 row_number 单行草案，但仍需证明缓存 `YES`、多匹配策略 `Use Any Value` 下的行选择顺序。`MP_STAGE_QD_LCCONT_SELF_NOTPAID` 的多个 Lookup 输入在 XML 中无上游 CONNECTOR，下一步继续追默认值、端口表达式、变量、SQL alias 或 unconnected lookup 语义。
- ODBC/JDBC_ODBC 归为 FDL datasourceType 映射或通用 JDBC adapter 待实现；`scripts/audit_infa_connections.py --connection-type-overrides ...` 可模拟产品适配配置，属于转换器/FDL 适配项。

当前已新增本地 DDL 生成/执行辅助脚本：

- `scripts/generate_infa_oracle_ddl.py`
- `scripts/OracleSqlRunner.java`

## 本地环境

FDL 安装在 Windows：

`D:\FDL-tomcat-win64`

WSL 可访问路径：

`/mnt/d/FDL-tomcat-win64`

Informatica 历史导出样例在 Windows：

`D:\项目文档\2-项目\百年人寿`

WSL 可访问路径：

`/mnt/d/项目文档/2-项目/百年人寿`

本地已安装插件目录：

`/mnt/d/FDL-tomcat-win64/webapps/webroot/WEB-INF/plugins/plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0`

FDL 自带 JDK8：

`/mnt/d/FDL-tomcat-win64/jdk`

已确认版本：

`OpenJDK 1.8.0_482`

注意：WSL 当前没有全局 Linux `java`、`gradle`，但 FDL 自带 Windows JDK8 可用。

## 插件工程特征

该仓库不是标准 Gradle 工程。

核心目录：

- `plugin-src/plugin.xml`
- `plugin-src/build.sh`
- `plugin-src/src/com/fr/plugin/tptj/ivan/jsd19083/...`

`plugin-src/build.sh` 引用：

- `jar-contents`
- `lib/*.jar`
- `pluginkit-10.0-20231122.jar`

这些构建依赖没有完整入库。后续需要从本地已安装插件目录或 FDL `WEB-INF/lib` 补齐可重复构建基线。

## 已确认样例 XML

样例目录中有：

- `auditdb.xml`
- `cc_lis_20260521.xml`
- `WF_HNC.XML`
- `WF_PRIP_Stage.XML`

统计结果：

| 文件 | Workflow | Worklet | Session | Mapping | MappingVariable | WorkflowVariable | 主要风险 |
|---|---:|---:|---:|---:|---:|---:|---|
| `cc_lis_20260521.xml` | 2 | 0 | 4 | 4 | 0 | 64 | 最适合 smoke test |
| `WF_HNC.XML` | 1 | 3 | 1 | 1 | 3 | 81 | Worklet 复用同一个 Session |
| `WF_PRIP_Stage.XML` | 1 | 12 | 199 | 199 | 476 | 2744 | Decision、Java SOAP、海量 Expression |
| `auditdb.xml` | 10 | 0 | 22 | 22 | 0 | 348 | Filter、Lookup Procedure、ODBC |

字段级 lineage 离线审计结果：

| 文件 | 功能状态 | 审计行 | 同名映射 | LOW | MEDIUM | HIGH | 说明 |
|---|---|---:|---:|---:|---:|---:|---|
| `cc_lis_20260521.xml` | `FDL_TRANSLATION_READY` | 377 | 100.0% | 377 | 0 | 0 | 同名透传已覆盖 |
| `WF_HNC.XML` | `XML_SEMANTICS_PARSED_TRANSLATION_PENDING` | 315 | 100.0% | 0 | 315 | 0 | 3 个 Worklet 复用同一 Session，字段同名但经过 Expression 透传 |
| `WF_PRIP_Stage.XML` | `XML_SEMANTICS_PARSED_TRANSLATION_PENDING` | 7475 | 96.2% | 1659 | 5527 | 289 | G10/G11 已执行化严格 success-AND Decision/status 子集并完成 API adapter contract；G12 已补 `OUT_ERRORINFO` 后置投影；剩余是 Target 写入语义、未连接字段和 API binding 生成 |
| `auditdb.xml` | `XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET` | 760 | 81.7% | 395 | 12 | 353 | G7 已让 3 组安全等值 Lookup + Filter 进入 FDL dataflow；2 组非等值 Lookup 需要单行输出语义；ODBC/JDBC datasourceType 与 unresolved 输入待翻译 |

插件实现状态：

- 已实现通用字段级映射 lineage：解析 `MAPPING/CONNECTOR`，对每个 Target 字段反向寻找 Source Qualifier / Source Definition 读字段候选。
- 已接入 FDL 字段映射生成：`SyncTaskNodeBean` 现在优先用 lineage 候选生成 `FieldTransferBean`，找不到明确 lineage 时才回退历史同名匹配。
- 已支持纯透传和纯改名 Expression 的字段映射链路；G6 后安全 Lookup / Filter 可通过 FDL dataflow 执行，不再只靠 `_infa_semantics` 备注。
- G2 后 Java/SOAP/API 不再静默丢失：会生成可见 FDL API 数据流节点，保留调用信息、请求/响应字段和结果落表映射。
- G5 后 ODBC/JDBC_ODBC 不再静默伪装成 MySQL 已完全支持：会保留连接名、原始类型、SQL、映射状态和 FDL datasourceType/通用 JDBC adapter 待实现说明；可用连接名覆盖配置提供产品适配。
- G6 后 `auditdb.xml` 中 `MP_STAGE_AML_FAILUREEND_TRANSACTION`、`MP_STAGE_AML_RISKLEVEL_TIMELIMIT`、`MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` 的安全等值 Lookup + Filter 会生成 `DBRead(base) + DBRead(lookup) -> SparkSqlNode -> DBWrite`。
- G7 后 Lookup 条件解析支持 `=`、`!=`、`<>` 和多条件 `AND`；`MP_STAGE_LIS_AML_SAMEAPP_QY` / `MP_STAGE_LIS_AML_SAMEAPP_BQ` 的非等值 join 已升级为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，已生成单行 Lookup 输出草案，但还需证明 multiplicity / first-match / cache policy 的行选择顺序。
- G8/G9 后 Workflow 条件、Decision 表达式和外部 Java/SOAP/API 调用都有结构化状态输出。G10 后 `WF_PRIP_Stage.XML` 中严格 success-AND Decision/status 门控已执行化；G11 后外部 SOAP 调用为 `FDL_ADAPTER_REQUIRED`，会继续生成 FDL API 节点并备注行级调用条件、HTTP status 映射和 adapter contract。
- 未把通用 Decision 任务状态变量/Decision 输出变量、API adapter contract、复杂计算型 Expression、非等值 Lookup、输入无法追溯的 Lookup、ODBC/JDBC datasourceType 伪装成已完整翻译；这些都是后续 FDL 结构生成、adapter 或运行能力待实现项。

## 当前插件主链路

上传 XML 后大致调用链：

`PluginController.dealWork`

-> `PluginServiceImpl.saveWork`

-> `XmlParserUtil.parseAll`

-> `DataSyncParser.parseAllWorkflows`

-> `OfflineDefinitionService.saveWork`

当前插件支持多 Workflow、Worklet、Session、Source Qualifier SQL、Pre/Post SQL、目标表、基础连线、部分参数和一个 Java Transformation 到 API 节点的初步转换。

G1 后补充：当前插件会显式保留 Workflow/Worklet 条件线，并把 Decision 节点生成到 FDL 图上。`WF_PRIP_Stage.XML` 中 `判定 -> s_M_PRIP_ExtractData` 的 `$判定.Condition = true` 会进入 FDL 条件节点和连线元数据，不再静默丢失。

G2/G14 后补充：当前插件会把 `WF_PRIP_Stage.XML` 中 `s_M_PRIP_ExtractData` / `M_PRIP_ExtractData` 的 Java SOAP 调用生成为 adapter contract 可视节点，不再生成原生 FDL API 数据流节点。UI 中应能看到 POST URL、SOAP body 动作 `doExtractDate`、空 `SOAPAction` 及来源、请求字段 `STATE <= SQ_EXTRACTDATALOG.STATE`、结果字段 `OUT_STATE -> STATE`、`nativeNode=not_generated` 和限制说明。

G3/G12/G14 后补充：当前插件会显式解析 Expression 字段并生成可见说明。普通 DB 同步中可安全转换且仅依赖 SQL 来源字段的 Expression 会作为外层 SQL SELECT 投影追加；依赖 Java/API/Lookup 等非 SQL 上游的 Expression 不会被下推到 source SQL。API 后置投影路径当前降级为 adapter contract：当 Expression 依赖可全部绑定到 source reader 字段和当前 API 输出端口时，输出 `FDL_API_POST_PROJECTION_ADAPTER_REQUIRED`，等待 adapter 提供 `_fdl_adapter_row_id` 行上下文后再生成原生 FDL dataflow。`WF_PRIP_Stage.XML` 中 `EXPTRANS.OUT_ERRORINFO -> EXTRACTDATALOG.ERRORINFO` 的 `DECODE(STATE,'0','informatic通知失败',ERRORINFO)` 已命中该机制；剩余是 API adapter contract 的可执行 FDL 绑定生成。

G4 后补充：插件开始显式解析 Filter 与 Lookup Procedure。含 Filter / Lookup 的普通 DB 同步会新增 `*_infa_semantics` 可视节点，并在同步节点、reader、writer、目标字段注释中展示原始条件/配置、依赖字段、输入输出字段、目标字段和限制。G4 阶段 Filter 只有在条件可转换且依赖全部来自 Source Qualifier / Source Definition 时才下推为外层 SQL WHERE；Lookup 只有在源可确定、条件为等值 AND、输入字段直接来自 SQL reader 且连接兼容时才生成外层 LEFT JOIN。后续 G6/G7 已把部分安全场景推进到 FDL dataflow。

G5 后补充：当前插件会显式解析 `SESSIONEXTENSION/CONNECTIONREFERENCE`。每个连接会保留 Informatica 原始 `CONNECTIONTYPE`、`CONNECTIONSUBTYPE`、用途（READER/WRITER/LOOKUPEXTENSION）、Session、Mapping、FDL `datasourceType`、映射状态和限制。`Oracle` 等安全类型映射为 `oracle`；`ODBC` / `JDBC_ODBC` 不再静默当作 MySQL，当前归为 FDL datasourceType 映射或通用 JDBC adapter 待实现。可通过 `-Dinfa.fdl.connection.type.overrides=training_uat=oracle,agentchannel=sqlserver` 或环境变量 `INFA_FDL_CONNECTION_TYPE_OVERRIDES=training_uat=oracle,agentchannel=sqlserver` 按连接名补产品适配类型。

G6 后补充：插件会优先把安全 Lookup / Filter 放入 FDL 数据流内部执行。实现路径为 `DBReadNode(reader)` 读取 Source Qualifier SQL，`DBReadNode(lookup_*)` 读取 Lookup SQL override 或 lookup 表，`SparkSqlNode(infa_lookup_filter)` 执行 LEFT JOIN 和 WHERE，最后 `DBWriteNode(writer)` 写目标表。第一阶段支持等值 Lookup、lookup SQL/table 为只读 SELECT、输入字段可通过纯透传/改名 Expression 追溯到 SQL reader、连接类型为 G5 SAFE 或显式 CONFIGURED；支持 `isnull(X)=false` 等可安全转为 SQL 条件的 Filter。`s_MP_STAGE_AML_FAILUREEND_TRANSACTION` 现在应生成 `reader + lookup_LKPTRANS_1 -> infa_lookup_filter -> writer`，Spark SQL 中包含 `DEALNO = PLAN3` 的 LEFT JOIN 和 `RERATINGDATE IS NOT NULL` 过滤。G6 阶段含 `!=` 的 Lookup 仍待翻译；当前已升级为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`。

G7 后补充：Lookup 条件不再只用 equality-only 粗判断，而是解析为谓词列表并逐个确认 lookup 侧字段与 base reader 输入字段。等值场景继续自动执行化；非等值场景会输出候选 join predicate，并标记为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，已按 Informatica first-match/cache policy 方向生成 row_number 单行 Lookup 草案。下游 Filter 也遵守上游状态：只有 Lookup 已执行化时才进入同一个 Spark SQL WHERE；上游 Lookup 只有单行计划时，Filter 也只标为计划就绪，不标记为完全可执行。

G8 后补充：Workflow/Worklet 条件线会被解析为 `EXECUTABLE`、`FDL_GENERATION_RESEARCH_PENDING`、`XML_SEMANTICS_PARSED_TRANSLATION_PENDING` 或 `FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED`。简单安全参数/字面量条件会写入 FDL condition entity；引用 Informatica 任务状态或 Decision 输出变量的条件作为 FDL runtime binding 待调研分支保留。`WF_PRIP_Stage.XML` 的 Decision 表达式是多个 Worklet `.Status = succeeded` 的 AND 聚合，`$判定.Condition = true` 是 Decision 输出变量引用；G8 阶段二者均归为 `FDL_GENERATION_RESEARCH_PENDING`，G9 已细分为 task-status condition 与 Decision output binding。

G9 后补充：`.Status` / `.Condition` 不再只是泛泛运行时变量。`$TaskName.Status = succeeded`、`!= failed`、`= disabled` 以及 AND/OR 组合会解析为 task-status predicates，known Informatica status literal 会归一为 `SUCCEEDED`、`FAILED`、`DISABLED` 等通用状态；`$DecisionName.Condition = true` 会解析为 Decision output binding。由于 FDL 当前公开 API 未证明普通 condition entity 可直接消费上游任务状态或 Decision 输出，生成结果归为 FDL adapter/runtime binding 待实现路径，并在 Decision/条件节点备注中列出 required runtime bindings。

G10 后补充：针对可证明的窄场景，Decision/status 已有原生 FDL 执行路径。若 Decision 表达式是 `AND` 聚合，所有谓词都是直接上游 `$Task.Status = succeeded`，这些上游任务到 Decision 的 `WORKFLOWLINK` 均为空条件，且 `TASKINSTANCE/TREAT_INPUTLINK_AS_AND=YES`，插件会使用 FDL 连线 `ON_SUCCESS` 作为上游成功门控，并把 `$Decision.Condition = true` 出分支转换为可执行 literal true 条件。`WF_PRIP_Stage.XML` 命中该模式，审计状态已变为 `EXECUTABLE_TASK_STATUS_CONDITION` + `EXECUTABLE_DECISION_OUTPUT_BINDING`。非 `succeeded` AND 成功门、OR 聚合、disabled/running/skipped 等仍保持 FDL runtime binding 或 adapter 待实现状态。

G8/G11 API 后补充：外部 Java/SOAP/API 调用会按 endpoint、protocol、request mapping、response mapping 和 adapter binding 判断为 `FDL_TRANSLATION_READY`、`FDL_ADAPTER_REQUIRED`、`FDL_GENERATION_RESEARCH_PENDING`、`FDL_ADAPTER_REQUIRED` 或 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`。`WF_PRIP_Stage.XML` 的 SOAP 调用有 endpoint/body/request/response mapping，且 G11 已证明行级调用条件字段 `STATE` 绑定到请求输入、HTTP 200/default 输出绑定到 `OUT_STATE -> STATE`，因此状态为 `FDL_ADAPTER_REQUIRED`。若后续遇到只有 Java class/method 而无可执行协议配置的外部调用，会生成 adapter 占位节点而不是假装 API 可运行。

## 功能翻译缺口

当前插件还没有完成全部 Informatica XML 语义到 FDL 结构的通用功能翻译。

P0/P1 缺口：

1. `WF_PRIP_Stage.XML` 的 Decision/status 门控已不再是当前核心缺口：该文件命中 G10 的原生 FDL `ON_SUCCESS` AND 门控模式，`$判定.Condition = true` 出分支已转换为可执行 literal true 条件。其他 `.Status`/`.Condition` 组合只有命中同一严格模式时才可执行，否则归为 FDL runtime binding 或 adapter 待实现。
2. G3/G11/G12 已补 Expression 解析与可见化，并对普通 DB 同步支持安全 SQL 投影下推；依赖 Java/API 输出的 Expression 只有在 API 为 FDL translation ready/generated adapter contract 且所有依赖可绑定到 source reader/API 输出端口时才进入 FDL dataflow 后置投影。`WF_PRIP_Stage.XML` 的 `OUT_ERRORINFO` 已命中该机制，剩余是可执行 FDL API/adapter binding 生成。
3. `auditdb.xml` 中的 3 组安全 Oracle 等值 Lookup + Filter 已进入 FDL dataflow 执行；`MP_STAGE_LIS_AML_SAMEAPP_QY` / `MP_STAGE_LIS_AML_SAMEAPP_BQ` 的 `!=` 非等值条件已解析，下一步生成符合 multiplicity / first-match / cache policy 的单行 Lookup 输出；`MP_STAGE_QD_LCCONT_SELF_NOTPAID` 输入仍需继续追 XML 中的默认值、端口表达式、变量、SQL alias 或 unconnected lookup 语义。
4. G8/G10/G11 已把 Java SOAP/API 调用升级为状态化迁移：endpoint/protocol/request/response mapping 足够时生成 FDL API 节点，不足时生成 adapter 占位。当前样例 SOAP 调用是 `FDL_ADAPTER_REQUIRED`；已解析出 HTTP 200 -> output/default output 映射，并证明状态映射字段与行级条件字段都绑定到 API 端口。剩余是把 adapter contract 落成可执行 FDL 原生配置或插件 adapter。
5. ODBC 连接 `agentchannel`、`training_uat` 已不再静默硬映射为 MySQL；当前保留连接名、原始类型和 SQL，并归为 FDL datasourceType 映射或通用 JDBC adapter 待实现。
6. 参数策略启发式较强，尤其日期参数自动保存为全局参数，后续要按 XML 作用域、默认值、持久化属性和引用位置翻译。

## G1 验证

离线条件审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_workflow_conditions.py
```

预期结果：

- `WF_PRIP_Stage.XML: decisions=1 conditional_links=1`
- `DECISION WORKFLOW/WF_PRIP_Stage: 判定`
- `CONDITION WORKFLOW/WF_PRIP_Stage: 判定 -> s_M_PRIP_ExtractData: $判定.Condition = true`
- `cc_lis_20260521.xml`、`WF_HNC.XML`、`auditdb.xml` 条件线数量为 0。

UI 验证步骤：

1. 不要直接覆盖 FDL 运行目录插件；先在 FDL 插件管理页面安装本次构建产物 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
2. 在插件导入入口选择 `/mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML`。
3. 生成后打开任务 `WF_PRIP_Stage`。
4. 预期图上能看到名为 `判定` 的条件/判断节点，不应只剩一条从上游 Worklet 直接到 `s_M_PRIP_ExtractData` 的普通线。
5. 查看 `判定` 到 `s_M_PRIP_ExtractData` 的分支或节点备注，预期能看到原始条件 `$判定.Condition = true`。
6. 继续打开 `cc_lis_20260521.xml` 和 `WF_HNC.XML` 的基础任务，预期原有数据同步节点、SQL 节点、字段映射仍能打开，不应因为 G1 新增条件节点而改变无条件样例的基础导入能力。

## G2 验证

离线 API 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_api_calls.py /mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML
```

预期结果：

- `WF_PRIP_Stage.XML: external_calls=1`
- `SESSION s_M_PRIP_ExtractData / MAPPING M_PRIP_ExtractData`
- `TRANSFORMATION Java [Custom Transformation / Java Transformation]`
- `METHOD POST`
- `ACTION doExtractDate`
- `SOAPAction  (source: "")`
- `REQUEST_FIELD STATE <= SQ_EXTRACTDATALOG.STATE [Source Qualifier]`
- `RESPONSE_FIELD OUT_STATE -> STATE`
- `STATUS_MAPPING OUT_STATE <= 1 when HTTP status 200; default OUT_STATE <= 0; source method sendAndGetResponseData`
- 限制应明确出现：行级 Java 调用条件需要验证，HTTP 状态码到输出字段的映射需要 adapter/运行证明。

UI 验证步骤：

1. 不要直接覆盖 FDL 运行目录插件；先在 FDL 插件管理页面安装本次构建产物 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
2. 在插件导入入口选择 `/mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML`。
3. 生成后打开任务 `WF_PRIP_Stage`，定位条件线后的 `s_M_PRIP_ExtractData_external_call_adapter` 可视节点。
4. 打开该节点或查看节点备注，预期能看到 Java/API/SOAP 调用 adapter contract；不应再出现 `source_reader -> api_reader` 连线，也不应出现 `Datasource type is API`。
5. 查看节点备注，预期能看到 `POST`、URL `http://20.12.2.153:8403/services/ExtractionPripDataService?wsdl`、`Content-Type`、`SOAPAction` 空值，以及 SOAP body 中的 `doExtractDate`。
6. 查看节点备注或说明，预期能看到请求字段 `STATE <= SQ_EXTRACTDATALOG.STATE [Source Qualifier]`、结果字段 `OUT_STATE -> STATE`、原 Java 条件 `"1".equals(STATE)`、HTTP 200 -> `OUT_STATE=1` / default `OUT_STATE=0` 状态映射、`nativeNode=not_generated` 和 adapter 语义限制。
7. 不再期待 API 读字段直接映射到目标字段；该路径在 adapter 落地前保持 `FDL_ADAPTER_REQUIRED`。
8. 回归导入 `cc_lis_20260521.xml` 和 `WF_HNC.XML`，预期没有新增 API 节点、基础同步节点仍能打开。

## G3 验证

离线 Expression 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_expressions.py /mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML
```

预期结果：

- `WF_PRIP_Stage.XML: expression_fields=5816 computed=1 sql_pushdown=0`
- `M_PRIP_ExtractData/EXPTRANS.OUT_ERRORINFO -> EXTRACTDATALOG.ERRORINFO`
- 表达式为 `DECODE(STATE,'0','informatic通知失败',ERRORINFO)`。
- 依赖为 `STATE, ERRORINFO`。
- 输出 `fdl_dataflow_status: FDL_API_POST_PROJECTION_ADAPTER_REQUIRED`。
- 证据为 API adapter row-bound projection required，`nativeNode=not_generated`；剩余是 API adapter contract 的可执行 FDL 绑定生成。

UI 验证步骤：

1. 不要直接覆盖 FDL 运行目录插件；先在 FDL 插件管理页面安装本次构建产物 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
2. 在插件导入入口选择 `/mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML`。
3. 生成后打开任务 `WF_PRIP_Stage`，定位 `s_M_PRIP_ExtractData_expression` 和 `s_M_PRIP_ExtractData_external_call_adapter`。
4. 打开 `s_M_PRIP_ExtractData_expression` 或查看节点备注，预期能看到 `EXPTRANS.OUT_ERRORINFO -> ERRORINFO`、表达式原文和依赖字段。
5. 打开 `s_M_PRIP_ExtractData_external_call_adapter` 可视节点，预期备注包含 API adapter contract、`FDL_API_POST_PROJECTION_ADAPTER_REQUIRED`、`nativeNode=not_generated`、`OUT_STATE -> STATE` 和 `_fdl_adapter_row_id` 行上下文要求；不应出现内部 `source_reader -> api_reader` 连线。
6. 回归导入 `cc_lis_20260521.xml` 和 `WF_HNC.XML`，预期基础同步节点仍能打开；`WF_HNC.XML` 的 Expression 应显示为透传，不应新增计算限制。

## G4 验证

离线 Filter / Lookup 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_filters_lookups.py /mnt/d/项目文档/2-项目/百年人寿/auditdb.xml
```

预期结果：

- `summary: filters=5 lookups=6`
- 能看到 `MP_STAGE_AML_FAILUREEND_TRANSACTION` / `MP_STAGE_AML_RISKLEVEL_TIMELIMIT` / `MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` 的 Filter 条件 `isnull(FIANLRATINGDATE)=false` 或 `isnull(RERATINGDATE)=false`，并显示 SQL 条件候选与“依赖 Expression/Lookup/API/non-SQL 上游”的限制。
- 能看到 `MP_STAGE_LIS_AML_SAMEAPP_QY`、`MP_STAGE_LIS_AML_SAMEAPP_BQ` 的 Lookup 条件包含 `SALARY_OTHER != ...`，因此不是安全 SQL join。
- 能看到每个 Lookup 的 connection、Lookup SQL override 是否存在、input mappings、output fields、target fields 和 conversion_status。

UI 验证步骤：

1. 不要直接覆盖 FDL 运行目录插件；先在 FDL 插件管理页面安装本次构建产物 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
2. 在插件导入入口选择 `/mnt/d/项目文档/2-项目/百年人寿/auditdb.xml`。
3. 生成后打开包含 Filter / Lookup 的任务，例如 `s_MP_STAGE_AML_FAILUREEND_TRANSACTION`、`s_MP_STAGE_AML_RISKLEVEL_TIMELIMIT`、`s_MP_STAGE_LIS_AML_SAMEAPP_QY`、`s_MP_STAGE_LIS_AML_SAMEAPP_BQ`。
4. 预期每个相关数据同步组前能看到 `*_infa_semantics` 可视节点；打开节点备注或同步节点备注，应能看到 Filter 条件、Lookup condition、输入字段、输出字段、目标字段和限制。
5. 对 `s_MP_STAGE_LIS_AML_SAMEAPP_QY` / `s_MP_STAGE_LIS_AML_SAMEAPP_BQ`，预期能看到 `CONTNO_OTHER` 过滤条件和 `SALARY_OTHER != SALARY_IN` 的 Lookup 限制，不应显示为完全 SQL join 成功。
6. 回归导入 `cc_lis_20260521.xml`、`WF_HNC.XML`、`WF_PRIP_Stage.XML`，预期基础数据同步、Worklet、G1 条件线、G2 API 节点、G3 Expression 说明仍可见。

## G5 验证

离线连接审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_connections.py /mnt/d/项目文档/2-项目/百年人寿/auditdb.xml
```

预期结果：

- `summary: connection_refs=50 lookup_refs=6 risks=7`
- `training_uat` 出现 4 个 `READER` 风险，原始类型为 `Relational/ODBC`，状态为 `FDL_ADAPTER_REQUIRED`。
- `agentchannel` 出现 3 个 `READER` 风险，原始类型为 `Relational/ODBC`，状态为 `FDL_ADAPTER_REQUIRED`。
- Lookup 连接 `qd_214_uat_r`、`aml_171_uat_r`、`lis_uat152` 以 `LOOKUPEXTENSION` 出现，原始类型为 `Relational/Oracle`，FDL 映射为 `oracle`。
- ODBC 限制说明必须包含 FDL datasourceType 映射或通用 JDBC adapter 待实现原因，并说明当前 `fdlDatasourceType=mysql` 只是可打开结构占位，不是 MySQL 完成结论。

UI 验证步骤：

1. 不要直接覆盖 FDL 运行目录插件；先在 FDL 插件管理页面安装本次构建产物 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
2. 在插件导入入口选择 `/mnt/d/项目文档/2-项目/百年人寿/auditdb.xml`。
3. 打开 `s_MP_STAGE_TRAIN_WITHOUT_TRAINING`、`s_MP_STAGE_TRAIN_ENTER_OVERTIME`、`s_MP_STAGE_TRAIN_FALSE_TRAINING` 或 `s_MP_STAGE_TRAIN_WITHOUT_ANY_TRAIN`，查看 `*_infa_connections` 节点或同步节点/reader/writer 备注，预期看到 `training_uat`、`Relational/ODBC`、`FDL_ADAPTER_REQUIRED` 和 FDL datasourceType/通用 JDBC adapter 待实现说明。
4. 打开 `s_MP_STAGE_AGENTCHNL_USERRIGHT_EXCEPTION`、`s_MP_STAGE_AGENTCHNL_SYS_MENU_OPTION` 或 `s_MP_STAGE_AGENTCHNL_LA_CHARGE_WITHHOLDING`，预期看到 `agentchannel` 的同类 ODBC 适配说明。
5. 打开 `s_MP_STAGE_QD_LCCONT_SELF_NOTPAID`、`s_MP_STAGE_AML_FAILUREEND_TRANSACTION`、`s_MP_STAGE_AML_RISKLEVEL_TIMELIMIT`、`s_MP_STAGE_AML_CUSTOMER_LEVELOVERDUE`、`s_MP_STAGE_LIS_AML_SAMEAPP_SAMEOCC_QY`、`s_MP_STAGE_LIS_AML_SAMEAPP_SAMEOCC_BQ`，查看 `*_infa_semantics` 节点或同步节点备注，预期能看到 Lookup 使用的连接 `qd_214_uat_r`、`aml_171_uat_r`、`lis_uat152` 以及 `LOOKUPEXTENSION` 用途。
6. 如已配置 FDL datasourceType 产品适配，可用 `INFA_FDL_CONNECTION_TYPE_OVERRIDES=training_uat=oracle,agentchannel=sqlserver` 或 JVM 参数 `-Dinfa.fdl.connection.type.overrides=training_uat=oracle,agentchannel=sqlserver` 后重新导入验证。

## G6 验证

离线 Filter / Lookup 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_filters_lookups.py /mnt/d/项目文档/2-项目/百年人寿/auditdb.xml
```

预期结果：

- `MP_STAGE_AML_FAILUREEND_TRANSACTION` 的 `LKPTRANS` 为 `FDL_DATAFLOW_EXECUTABLE`，`input_read_aliases` 包含 `PLAN3_IN->PLAN3`。
- `MP_STAGE_AML_FAILUREEND_TRANSACTION` 的 `FILTRANS` 为 `FDL_DATAFLOW_EXECUTABLE`，`sql_condition` 为 `RERATINGDATE IS NOT NULL`。
- `MP_STAGE_AML_RISKLEVEL_TIMELIMIT`、`MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` 的等值 Lookup + Filter 也应为 `FDL_DATAFLOW_EXECUTABLE`。
- G6 基线中 `MP_STAGE_LIS_AML_SAMEAPP_QY`、`MP_STAGE_LIS_AML_SAMEAPP_BQ` 含 `!=`，不能显示成完整翻译；当前预期为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，见下一节。

UI 验证步骤：

1. 不要直接覆盖 FDL 运行目录插件；先在 FDL 插件管理页面安装本次构建产物 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
2. 在插件导入入口选择 `/mnt/d/项目文档/2-项目/百年人寿/auditdb.xml`。
3. 打开任务 `s_MP_STAGE_AML_FAILUREEND_TRANSACTION` 的 `s_MP_STAGE_AML_FAILUREEND_TRANSACTION_query`。
4. 预期该节点内部不再只有 `reader -> writer`，而应看到 `reader`、`lookup_LKPTRANS_1`、`infa_lookup_filter`、`writer`。
5. 打开 `infa_lookup_filter`，预期 Spark SQL 包含 `$[reader]`、`$[lookup_LKPTRANS_1]`、`LEFT JOIN` 和 `RERATINGDATE IS NOT NULL`。
6. 打开 writer 字段映射，预期 `PLAN3` 来自 Lookup 输出 `DEALNO`，`RERATINGDATE`、`PLAN4` 来自 Lookup 输出，Filter 后再写入目标表。

## G7 验证

离线 Filter / Lookup 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_filters_lookups.py /mnt/d/项目文档/2-项目/百年人寿/auditdb.xml
```

预期结果：

- `MP_STAGE_AML_FAILUREEND_TRANSACTION`、`MP_STAGE_AML_RISKLEVEL_TIMELIMIT`、`MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` 的等值 Lookup + Filter 仍为 `FDL_DATAFLOW_EXECUTABLE`。
- `MP_STAGE_LIS_AML_SAMEAPP_QY`、`MP_STAGE_LIS_AML_SAMEAPP_BQ` 的 Lookup 条件会输出 parsed join predicates，状态为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，限制说明包含非等值 Lookup 的 multiplicity / first-match / cache policy 行选择顺序待证明原因。
- 上述两个 Mapping 的 Filter 条件 `isnull(CONTNO_OTHER)=false` 也应为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，不能在上游 Lookup 未完全执行化时单独标记为 executable。
- `MP_STAGE_QD_LCCONT_SELF_NOTPAID` 仍为 `FDL_GENERATION_RESEARCH_PENDING_UNSUPPORTED`，限制说明应列出 `MONEY1`、`TMAKE1`、`ADDMONEYINFO1`、`DEALTYPE1` 等 unresolved 输入。

生产代码硬编码 guardrail：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/check_no_sample_hardcoding.py
```

预期结果：

- 输出 `sample identifier hardcoding check passed`。
- 如果样例 XML、Mapping、Session 或字段名出现在 `plugin-src/src` 生产代码中，脚本应返回非 0 并打印文件与行号。

## G8 / G9 / G10 / G11 验证

FDL 运行库审计结论：

- `fdl-datadevelop-offline-4.2.jar` 存在 `ConditionComponent`、`DPConditionItem`、`ApiReadNode`、`SparkSqlNode`、`FilterDataNode`、`JoinDataNode`。
- `fdl-core-4.2.jar` 存在 `AndOrConditionEntity`、`CompareConditionEntity`、`SelfConditionEntity` 等条件实体。
- 未发现稳定的任务流 Branch/Gateway node entity；因此 G8 对 Decision 采用 FDL condition component + FDL adapter 待实现备注，而不是发明新节点类型。
- G9 复查还看到 `ComponentLine#lineCondition`、WorkRecord/ExecuteTaskStatus/OfflinePipeline record 等状态/记录结构；这些可以支持后续自定义 adapter 设计，但没有证明普通 FDL condition entity 可在分支执行时直接读取上游组件状态或 Decision 输出。
- G10 采用已确认存在的 `ComponentLine#lineCondition`：直接上游成功门控由 FDL `ON_SUCCESS` 连线表达，Decision 出分支仅在严格 success-AND 模式下转换为 literal true 条件；这不是通用 task-status adapter。

Workflow 条件审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_workflow_conditions.py /mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML
```

当前输出要点：

- `WF_PRIP_Stage.XML: decisions=1 conditional_links=1 statuses={'EXECUTABLE_TASK_STATUS_CONDITION': 1, 'EXECUTABLE_DECISION_OUTPUT_BINDING': 1}`
- Decision 表达式引用 11 个 Worklet `.Status = succeeded`，G10 会输出 11 个 `TASK_STATUS_PREDICATE ... normalized=SUCCEEDED executable=true`。
- 可执行证据：所有谓词任务都有到 Decision 的直接空条件入线，Decision `TREAT_INPUTLINK_AS_AND=YES`，FDL 入线使用 `ON_SUCCESS`。
- `$判定.Condition = true` 会输出 `DECISION_OUTPUT_REFERENCE ... executable=true`；该出分支仅因上游 success-AND 门控已由 FDL 原生连线表达才可执行化。

API 调用审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_api_calls.py /mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML
```

当前输出要点：

- `WF_PRIP_Stage.XML: external_calls=1 statuses={'FDL_ADAPTER_REQUIRED': 1}`
- 协议为 `SOAP_HTTP`，有 POST URL、SOAP body、请求字段和 `OUT_STATE -> STATE` 响应字段映射。
- 已解析 `STATUS_MAPPING OUT_STATE <= 1 when HTTP status 200; default OUT_STATE <= 0; source method sendAndGetResponseData`。
- Parsed adapter contract 证据：`INVOCATION_CONDITION "1".equals(STATE) fields=STATE`，`rowInvocationMode=predicate-before-request`，`rowInvocationFields=STATE`，`httpStatusOutputBinding=parsed-from-java-getResponseCode`。
- 边界：该状态证明 adapter contract 完整，剩余是 FDL 原生 API 状态码字段绑定或插件 adapter 生成。

Expression 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_expressions.py --no-write /mnt/d/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML
```

当前输出要点：

- `expression_fields=5816 computed=1 sql_pushdown=0`
- `EXPTRANS.OUT_ERRORINFO` 输出 `FDL_API_POST_PROJECTION_ADAPTER_REQUIRED`，证据为 API adapter row-bound projection required，`nativeNode=not_generated`。
- 边界：API 行级调用与 HTTP status binding 已解析，剩余是 adapter contract 的可执行 FDL 绑定生成。

## 本地 FDL 连接清单

本地 FDL 需要按 XML 中的 `CONNECTIONNAME` 新建同名数据连接。

当前状态：用户已在本地 FDL 手动新建好这些名称的数据连接，可以进入导入/生成验证阶段。

Oracle：

- `prip_155`
- `lis_uat152`
- `qd_214_uat_r`
- `crm133`
- `xqgl119`
- `east`
- `auditdb_99_uat_rw_sjods_user`
- `aml_171_uat_r`
- `cem_214_uat_r`
- `cc_133_uat_rw`
- `lis_190_uat_newcrm_rw`

ODBC/G5 后当前插件会生成可见 fallback，并标注 FDL datasourceType/通用 JDBC adapter 待实现：

- `agentchannel`
- `training_uat`

建议建库顺序：

1. Smoke test：`cc_133_uat_rw`、`lis_190_uat_newcrm_rw`
2. Worklet test：`prip_155`、`lis_uat152`
3. Full coverage：补齐全部连接

## 推荐下一步

第一步不是直接修某个转换，而是建立可重复验证基线：

1. 已完成：补齐插件构建输入，`plugin-src/build.sh` 可使用本机 FDL 自带 JDK8 和运行库打包。
2. 已完成：生成 baseline 插件包 `plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。
3. 已完成：用户通过 FDL UI 验证 `cc_lis_20260521.xml` 可以成功创建任务。
4. 已完成：从 `cc_lis_20260521.xml` 生成本地验证用 Oracle DDL，并在本地 `SYSTEM` schema 中建表。
5. 已完成：校验 5 张表字段数：`CC_ANSWER_INFO_LIS` 114、`CC_CALLCENTERRESULT` 12、`CC_CALLCENTERRESULT1` 12、`CC_CUSTOMER_INFO_LIS` 108、`CC_NB_FEEDBACK_INFO` 143。
6. 已完成：用户确认 `cc_lis` 当前没有问题。
7. 已完成：为 `WF_HNC.XML` 生成并执行本地 Oracle DDL，`LCCONT` 已确认 105 个字段，Source Qualifier SQL override 额外依赖的 `LCINSURED` 已确认 2 个字段。
8. 已完成：用户确认 `WF_HNC.XML` 当前看起来没有问题。
9. 已完成：建立离线字段级 lineage 审计工具，对四个 XML 输出目标字段、上游字段、经过的转换、当前插件同名映射可覆盖情况、不可自动确认的风险点。
10. 已完成：把通用字段级映射 lineage 接入插件生成链路，不再只是针对四个样例 XML 做审计。
11. 已完成 G1：保留 Workflow/Worklet 条件线和 Decision 节点，`WF_PRIP_Stage.XML` 的 `$判定.Condition = true` 不再静默丢失。
12. 已完成 G2：Java/SOAP/API 调用不再静默丢失，`WF_PRIP_Stage.XML` 的外部 SOAP 调用、`SOAPAction` 来源、请求/响应字段和 `OUT_STATE -> STATE` 落表映射会进入 FDL API 节点和节点备注。
13. 当前判断：`cc_lis` 字段级离线审计为 `FDL_TRANSLATION_READY`；`WF_HNC` 为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`，UI 可用主要来自同名字段和 Expression 透传；`WF_PRIP_Stage`、`auditdb` 仍有功能翻译待实现项。
14. 已完成 G3：Expression 字段解析、SQL 安全下推和待翻译语义可见化已接入；`WF_PRIP_Stage.XML` 唯一计算型 Expression 因依赖 Java/API 输出被明确标注限制。
15. 已完成 G4：Filter / Lookup Procedure 解析、可见化和保守 SQL 转换路径已接入；`auditdb.xml` 的 5 个 Filter 和 6 个 Lookup 不再静默丢失。
16. 已完成 G5：连接类型识别、ODBC/JDBC_ODBC 可见 fallback、连接名覆盖配置和离线连接审计已接入；`auditdb.xml` 的 `training_uat`、`agentchannel` 不再被静默当成 MySQL 完全支持。
17. 已完成 G6：FDL 4.2 jar/API 审计确认存在 `FilterDataNode`、`JoinDataNode`、`SparkSqlNode` 等转换节点；本阶段采用 `SparkSqlNode` 实现数据流内 transform。`MP_STAGE_AML_FAILUREEND_TRANSACTION`、`MP_STAGE_AML_RISKLEVEL_TIMELIMIT`、`MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` 的等值 Lookup + Filter 进入 `DBRead(base) + DBRead(lookup) -> SparkSqlNode -> DBWrite`。
18. 已完成 G7：Lookup 条件通用谓词解析、Source Qualifier / SQL read alias 辅助追踪、非等值 Lookup 待翻译状态、下游 Filter 链式状态保护和生产代码硬编码 guardrail 已接入。
19. 已完成 G8：Workflow 条件、Decision 表达式和外部 Java/SOAP/API 调用进入结构化状态输出。`WF_PRIP_Stage.XML` 的 Decision / `$判定.Condition = true` 在 G8 阶段为粗粒度 `FDL_GENERATION_RESEARCH_PENDING`；SOAP 调用为 `FDL_GENERATION_RESEARCH_PENDING`；API 输出依赖 Expression 已明确标注 external-call dependency。生产代码硬编码 guardrail 已覆盖 Decision/API 样例标识。
20. 已完成 G9：Decision 表达式中的 `.Status = succeeded` AND 聚合会输出结构化 task-status predicates；`$判定.Condition = true` 会输出 Decision output binding 和 required runtime bindings。未命中可证明原生模式时归为 adapter/runtime binding 待实现。
21. 已完成 G10：`WF_PRIP_Stage.XML` 命中严格 success-AND Decision 模式，审计变为 `EXECUTABLE_TASK_STATUS_CONDITION` + `EXECUTABLE_DECISION_OUTPUT_BINDING`；API 审计新增 HTTP status -> output/default output 解析和 adapter required config。
22. 已完成 G11：`WF_PRIP_Stage.XML` 的 Java/SOAP/API 调用升级为 `FDL_ADAPTER_REQUIRED`；auditdb 非等值 Lookup 补充缓存 `YES` / `Use Any Value` 证据，当前已升级为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，unresolved Lookup 输入和 ODBC/JDBC datasourceType 继续保留为待翻译项。
23. 已完成 G12：补 API 后置表达式 merge/projection，让 `EXPTRANS.OUT_ERRORINFO` 在 FDL dataflow 中合并 source reader 字段与 parsed adapter contract API 输出后投影；API adapter 剩余可执行 FDL 绑定生成。
24. 已完成 G13：Target/session 写入策略进入生成链路；insert/update/upsert 按 XML key fields 设置 FDL logical primary key 和 update strategy，writer truncate 使用 `TruncateWriteConf`，delete-only session 同连接且 key 可追溯时生成 `DELETE WHERE EXISTS(source SQL)`，否则生成 row-level delete adapter 待实现节点，不再误生成 DBWrite upsert。
25. 下一步建议优先把 API adapter contract 落成 FDL 原生配置或插件 adapter；之后实现非等值 Lookup 的可执行单行语义（multiplicity、first-match、cache policy）、处理 unresolved 输入字段、ODBC/JDBC datasourceType 和跨连接/key 不可追溯 delete adapter。

## 操作注意事项

- 不要直接覆盖 FDL 运行目录插件，除非用户明确要求部署验证。
- 部署前备份：
  `/mnt/d/FDL-tomcat-win64/webapps/webroot/WEB-INF/plugins/plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0`
- 不要打印或保存数据库密码、Token、Cookie、license。
- 用户只通过 FDL UI 验证，不应把“代码看起来正确”作为交付标准。
- 后续如果用户只问方案，可以先引用本文件、字段 lineage 审计报告和当前插件实现状态；如果用户要求继续做，则优先按复杂运行语义优先级生成 API adapter contract、实现非等值 Lookup、处理 unresolved 输入、ODBC/JDBC datasourceType 和参数作用域。
