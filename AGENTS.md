# AGENTS.md

本文件用于让新的 Codex/agent 对话快速恢复项目上下文。进入本仓库后，先读本文件，再读 `docs/project-handoff-2026-07-02.md` 和 `docs/migration-coverage-audit-2026-07-02.md`。

## 项目目标

本项目是帆软 FDL 插件 `com.fr.plugin.tptj.ivan.jsd19083` 的源码工作区，仓库来自：

`https://gitee.com/gongezh/infotofdl.git`

插件目标：作为通用 Informatica PowerCenter XML 到 FineDataLink 的功能转换器，按 XML 中记录的任务语义生成可执行或可调研的 FDL 结构。

当前判断口径不是按业务含义确认字段、接口或变量，而是按 Informatica XML 语义到 FDL 结构的功能翻译完整度判断。后续实现、测试和验收都必须区分：XML 已完整表达但插件尚未翻译、XML 已表达但 FDL 拼法待调研、XML 本身缺信息。

已确认：Informatica 调度配置不在当前范围内，当前范围只覆盖任务转换与运行语义。

已确认：用户不会审计代码，只会在 FDL 用户界面验证结果。因此后续交付必须提供可安装/可运行产物、明确 UI 验证步骤、可观察的成功/失败现象和回退方式。

## 本地关键路径

- 当前源码仓库：`/home/liufeng/code/bainianrenshou/infotofdl`
- 插件源码目录：`/home/liufeng/code/bainianrenshou/infotofdl/plugin-src`
- 本地 FDL 安装目录：`/mnt/d/FDL-tomcat-win64`，Windows 路径为 `D:\FDL-tomcat-win64`
- Informatica 样例 XML：`/mnt/d/项目文档/2-项目/百年人寿`，Windows 路径为 `D:\项目文档\2-项目\百年人寿`
- 本地已安装插件目录：`/mnt/d/FDL-tomcat-win64/webapps/webroot/WEB-INF/plugins/plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0`
- FDL 自带 JDK8：`/mnt/d/FDL-tomcat-win64/jdk`

## 环境事实

- WSL 当前没有全局 `java`、`javac`、`jar`、`gradle`。
- FDL 自带 JDK 可用，版本为 `OpenJDK 1.8.0_482`，包含 `java.exe`、`javac.exe`、`jar.exe`。
- 仓库不是标准 Gradle 工程，没有顶层 `build.gradle` / `settings.gradle`。
- 当前构建脚本是 `plugin-src/build.sh`，它引用 `jar-contents`、`lib/*.jar`、`pluginkit-10.0-20231122.jar`，这些依赖没有完整提交到仓库。
- 本机 FDL 已安装同名 `0.3.0` 插件，运行目录里的 `plugin.xml` 与仓库 `plugin-src/plugin.xml` 一致。

## 已有文档

- `docs/migration-coverage-audit-2026-07-02.md`：四个 XML 的迁移覆盖审计报告，包含能力缺口和本地 FDL 需要新建的数据连接名称。
- `docs/project-handoff-2026-07-02.md`：当前环境、项目状态、风险、下一步工作交接。
- `docs/build-and-ui-smoke-test.md`：可重复构建命令与第一轮 UI smoke test 操作步骤。
- `docs/next-session-prompt-field-lineage-audit-2026-07-02.md`：下一对话可直接复制的完整提示词，目标是做字段级 lineage 离线审计。
- `docs/lineage-audit/README.md`：字段级 lineage 离线审计总览，包含四个 XML 的功能翻译状态、CSV/Markdown 明细入口和下一步插件修复优先级。
- `docs/local-ddl/cc_lis_20260521/`：`cc_lis_20260521.xml` 的本地 Oracle 验证 DDL 和表字段数校验 SQL。
- `docs/local-ddl/WF_HNC/`：`WF_HNC.XML` 的本地 Oracle 验证 DDL 和表字段数校验 SQL。
- `docs/superpowers/specs/2026-05-24-informatica-to-fdl-plugin-design.md`：历史设计说明。
- `docs/superpowers/plans/2026-05-24-informatica-to-fdl-plugin.md`：历史实现计划。

## 样例 XML

样例目录中当前已确认有：

- `cc_lis_20260521.xml`
- `WF_HNC.XML`
- `WF_PRIP_Stage.XML`
- `auditdb.xml`

建议验证顺序：

1. `cc_lis_20260521.xml`：最简单，多 Workflow + 普通 Session。
2. `WF_HNC.XML`: Worklet + 可复用 Session。
3. `WF_PRIP_Stage.XML`: 大型 Worklet、Decision、Java SOAP API、海量参数。
4. `auditdb.xml`: 多 Workflow、Filter、Lookup Procedure、ODBC。

## 当前判断

当前插件可以作为通用转换器基础，但还没有覆盖所有 Informatica XML 功能语义到 FDL 的结构化翻译。主要 P0/P1 功能缺口：

1. Target / Session 写入语义还需要解析 insert、update、delete、truncate、key fields、update strategy 与 target load plan，并映射到 FDL 写入模式或 SQL。
2. Java/SOAP/API 已能解析 URL、body、请求字段、输出字段和 HTTP status 映射，后续要把行级请求条件与状态映射生成到 FDL 原生 API 配置或插件 adapter。
3. Expression、Filter、Lookup Procedure 已有部分 SQL/dataflow 转换；非等值 Lookup 还需要按 Informatica cache / multiple match policy 生成单行 Lookup 输出语义。
4. 未连接 Lookup 输入/输出要继续按 XML 中的端口方向、默认值、表达式、变量、SQL alias 或 unconnected lookup 语义追踪，不按业务字段名猜测。
5. ODBC/JDBC_ODBC 已解析出连接名、连接类型和 SQL；插件应实现 FDL datasourceType 映射或通用 JDBC 适配，不再把 MySQL 当成完成结论。
6. MappingVariable、WorkflowVariable、日期默认值和 `${Param}` 引用要按 Informatica 作用域、默认值、持久化属性和引用位置翻译，不按变量名启发式判断。

## 本地 FDL 连接准备

本地测试需要 FDL 数据连接名称与 XML 中 `CONNECTIONNAME` 完全一致。用户已在本地 FDL 手动新建好这些名称的数据连接，后续可以开始导入/生成验证。

Oracle:

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

ODBC / 通用 JDBC 适配待实现：

- `agentchannel`
- `training_uat`

最小 smoke test 只需要先建：

- `cc_133_uat_rw`
- `lis_190_uat_newcrm_rw`

Worklet test 再建：

- `prip_155`
- `lis_uat152`

## 开发原则

- 默认在 WSL 路径下操作源码。
- 不要直接修改 FDL 运行目录，除非用户明确要求部署验证；部署前必须备份原插件。
- 不要假设这是 Gradle 项目。
- 插件构建必须使用 JDK8。
- 先建立可重复构建与离线验证，再改解析逻辑。
- 对“直接可用”的判断必须包含任务创建、任务打开、节点配置、连线条件、参数、字段映射、运行结果对账。
- 用户只通过 UI 验证结果，不能依赖用户阅读代码确认正确性。
- 不要提交或打印任何数据库密码、Token、Cookie、license 等敏感信息。

## 下一步建议

P0：

1. 从本地已安装插件 jar 补齐 `jar-contents` 和 `pluginkit` 基线。
2. 使用 FDL 自带 JDK8 建立可重复打包脚本。
3. 建立离线解析/覆盖验证工具，输出任务、节点、连线、参数、连接、目标表、API 配置。

当前进展：`plugin-src/build.sh` 已支持使用 FDL 自带 JDK8 和本地 FDL 运行库打包，baseline 包已生成。第一轮 UI smoke test 步骤见 `docs/build-and-ui-smoke-test.md`。

2026-07-02 追加进展：用户已通过 UI 成功创建 `cc_lis_20260521.xml` 的任务。随后已用本地 Oracle `jdbc:oracle:thin:@localhost:11521/XEPDB1` 在 `SYSTEM` schema 中创建验证表，并确认字段数：`CC_ANSWER_INFO_LIS` 114、`CC_CALLCENTERRESULT` 12、`CC_CALLCENTERRESULT1` 12、`CC_CUSTOMER_INFO_LIS` 108、`CC_NB_FEEDBACK_INFO` 143。`CC_CALLCENTERRESULT1` 是 Target 实例名，生成本地验证 DDL 时必须保留 Target 实例名。用户已确认 `cc_lis` 当前没有问题。

2026-07-02 追加进展：已为 `WF_HNC.XML` 生成并执行本地 Oracle 验证 DDL，`LCCONT` 已确认 105 个字段。用户随后发现缺少 `LCINSURED`，已确认它不在 Source/Target Definition 中，而是在 `SQ_LCCONT` 的 Source Qualifier SQL override 里通过 `FROM lcinsured b` 引用，连接属于 `lis_uat152`。已补建 `LCINSURED`，确认 2 个字段，并修正 `scripts/generate_infa_oracle_ddl.py` 以自动输出 SQL override 依赖表文件。下一步应让用户在 FDL UI 重新验证 `WF_HNC.XML`。第一轮不要选择 UI 上的全局来源/去向数据连接，避免覆盖 XML 原始连接名。

2026-07-02 追加进展：用户确认 `WF_HNC.XML` 当前看起来也没有问题。需要注意，这只能证明 UI 层任务创建、打开和同名字段映射基本可用；用户无法通过 UI 审计字段级 lineage。因此后续已先建立离线字段级 lineage 审计报告，再把字段级映射 lineage 接入插件生成链路。

2026-07-02 追加进展：已新增 `scripts/audit_infa_field_lineage.py`，可直接在 WSL 中离线读取四个 XML，输出 `docs/lineage-audit/*.csv`、`docs/lineage-audit/*.md` 和总览 `docs/lineage-audit/README.md`。当前审计状态：`cc_lis_20260521.xml` 为 `FDL_TRANSLATION_READY`，377/377 字段同名透传；`WF_HNC.XML` 为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`，315/315 字段同名但经过 Expression 透传；`WF_PRIP_Stage.XML` 为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`，7475 行中 289 行 HIGH，重点是 Target 写入语义、Decision/条件线、Java SOAP/API、Expression；`auditdb.xml` 为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET`，760 行中 353 行 HIGH，重点是 Filter、Lookup Procedure、ODBC/JDBC datasourceType 和未连接 Lookup 输入。

2026-07-02 追加进展：插件已接入通用字段级映射 lineage，不是只针对四个样例 XML。新增 `FieldLineageInfo`，`DataSyncParser` 会解析 `MAPPING/CONNECTOR`，为 Target 字段反查 Source Qualifier / Source Definition 读字段候选，`SyncTaskNodeBean.createFieldTransferItems` 会优先按 lineage 候选生成 `FieldTransferBean`，找不到明确 lineage 时才回退历史同名匹配。已支持纯透传 Expression/Filter 字段链路；计算型 Expression、Lookup Procedure、Custom Transformation/Java API 不会被伪装成已支持。插件包已重新构建：`plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`。

2026-07-03 追加进展：G11 已把 `WF_PRIP_Stage.XML` 的 Java/SOAP/API 调用结构化为 `ADAPTER_REVIEWED_API`。依据是通用 adapter contract 已解析并绑定：行级调用条件字段 `STATE` 绑定到请求输入，HTTP 200/default 状态码输出绑定到 `OUT_STATE -> STATE`。G12 已补通用 API 后置 Expression merge/projection：当 Expression 依赖可全部绑定到 source reader 字段和当前 API 输出端口时，生成 `DBRead(source_reader) + ApiRead(api_reader) -> SparkSqlNode(infa_api_post_expression) -> DBWrite`；`EXPTRANS.OUT_ERRORINFO` 命中该机制，剩余工作是生成可执行 FDL API/adapter 绑定。`auditdb.xml` 非等值 Lookup 归为 `FDL_DATAFLOW_TRANSLATION_PENDING`，审计已补充缓存 `YES`、多匹配策略 `Use Any Value`；`MP_STAGE_QD_LCCONT_SELF_NOTPAID` 的 `MONEY1/TMAKE1/ADDMONEYINFO1/DEALTYPE1` 在 XML 中没有上游 CONNECTOR，下一步要继续追默认值、端口表达式、变量、SQL alias 或 unconnected lookup 语义。`training_uat`、`agentchannel` 的 ODBC/JDBC_ODBC 归为 FDL datasourceType/通用 JDBC 适配待实现。

P1：

1. 已完成字段级 lineage 离线审计，并已把字段级映射 lineage 接入插件运行链路；后续改复杂语义前先看 `docs/lineage-audit/README.md` 和对应 CSV 明细。
2. 保留 `WORKFLOWLINK/@CONDITION`，处理 `Decision` 节点。
3. 下一步优先把 `WF_PRIP_Stage.XML` API adapter contract 落成 FDL 原生 API 配置或插件 adapter；`EXPTRANS.OUT_ERRORINFO` 已有 source 字段 + API 输出的 FDL dataflow 后置投影，不再是缺 merge/projection blocker。
4. 补计算型 Expression 的对应 SQL 或 FDL 表达式；纯透传/纯改名字段映射已由 lineage 覆盖。
5. 将 Filter 转为 SQL WHERE 或 FDL 可执行逻辑。
6. 继续实现 Lookup Procedure 功能翻译；非等值 Lookup 必须按 XML 中的 cache / multiple match policy 生成单行 Lookup 输出语义，不能直接把普通 SQL join 当成完整翻译。

P2：

1. 完善参数作用域和默认值策略。
2. 增加连接名/连接类型映射配置。
3. 已清理硬编码 `D:/fdl_debug_*.txt` 调试文件输出。
