# 字段级 lineage 离线审计总览

生成方式：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_field_lineage.py
```

本审计只读取 Informatica XML，不启动 FDL，不修改 FDL 运行目录，不连接数据库。

外部接口/API 审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_api_calls.py
```

API 审计同样只读 XML，并对 password、token、cookie、license 等敏感值做脱敏。

Expression 专项审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_expressions.py
```

Filter / Lookup Procedure 专项审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_filters_lookups.py /mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/auditdb.xml
```

Filter / Lookup 审计同样只读 XML，不连接数据库，不修改 FDL 运行目录，并对 password、token、cookie、license 等敏感值做脱敏。

连接类型专项审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_connections.py
```

连接审计同样只读 XML，不连接数据库，不修改 FDL 运行目录，并对 password、token、cookie、license 等敏感值做脱敏。可用 `--connection-type-overrides training_uat=oracle,agentchannel=sqlserver` 或环境变量 `INFA_FDL_CONNECTION_TYPE_OVERRIDES=training_uat=oracle,agentchannel=sqlserver` 在离线审计中模拟同名 FDL 数据连接解析出的 datasourceType。

参数 / 变量作用域专项审计：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_params.py /mnt/d/项目文档/2-项目/百年人寿
```

参数审计同样只读 XML，不连接数据库，不修改 FDL 运行目录；只输出 XML 中的作用域、默认值、引用位置和待翻译状态，不按变量名猜日期或业务含义。

## 口径原则

- 本项目是通用 Informatica XML 到 FDL 的功能转换器，不按领域含义判断字段、接口或变量。
- XML 已表达但插件尚未生成的内容，归类为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`。
- XML 已表达但 FDL 原生节点/插件 adapter 拼法还没调研清楚的内容，归类为 `FDL_GENERATION_RESEARCH_PENDING`。
- XML 本身缺少密码等运行凭据时归类为 `XML_MISSING_RUNTIME_SECRET`；外部 Java 类完整实现体缺失时归类为 `XML_MISSING_EXTERNAL_CODE`。
- ODBC/JDBC_ODBC 只作为 Informatica 原始连接元数据保留；FDL 生成按连接名匹配已有数据连接，不按 ODBC 字样推断库类型。

## 插件实现状态

- 字段级 lineage 已接入插件生成链路：`DataSyncParser` 解析 `MAPPING/CONNECTOR`，`SyncTaskNodeBean` 优先按 lineage 生成字段映射，找不到明确 lineage 时才回退同名匹配。
- G1 已接入 Workflow/Worklet 条件控制流：`WORKFLOWLINK/@CONDITION`、Decision 节点和条件分支会进入 FDL 节点/连线元数据。
- G2 已接入通用 Java/SOAP/API 调用可见迁移：可解析外部调用 URL、HTTP 方法、headers、SOAPAction 来源、body、请求字段、输出字段到目标字段映射，并生成 FDL API 读 + DB 写数据流节点。
- G2 不把未生成的运行结构标成已完成：Java 行级调用条件、HTTP 状态码到输出字段的代码逻辑、复杂响应解析会作为待生成能力暴露，下一步落到 FDL 原生配置或插件 adapter。
- G3 已接入通用 Expression 元数据解析：普通 DB 同步在依赖均来自 Source Qualifier / Source Definition 且表达式可转换时追加 SQL 投影；尚未翻译的表达式会在同步/API 节点备注和字段注释中显式暴露。
- G4 已接入通用 Filter / Lookup Procedure 元数据解析：Filter 条件、依赖字段、输出字段、目标字段、SQL WHERE 候选和限制会进入真实同步/数据流节点备注、reader/writer 备注和目标字段注释；Lookup Procedure 的输入字段、输出字段、Lookup SQL/表、连接、条件、目标字段和限制同样可见。
- G4 对 Filter 采取保守 SQL 下推：只有条件可转换且依赖全部来自 Source Qualifier / Source Definition 时才包装外层 SQL WHERE；依赖 Expression / Lookup / API / 非 SQL 上游时保留 XML 语义并标为待生成 FDL 结构。
- G4 对 Lookup Procedure 采取分层翻译：已可确定的等值 Lookup 生成 SQL/dataflow；其余 Lookup 保留完整 XML 语义并标记为插件待翻译或 FDL 拼法待调研。
- G5 已接入通用连接名匹配：每个 `SESSIONEXTENSION/CONNECTIONREFERENCE` 会保留连接名、原始类型/子类型、用途、Session/Mapping、FDL datasourceType、映射状态和限制。ODBC/JDBC_ODBC 不再作为 datasource adapter 缺口；FDL 生成会按同名 FDL 数据连接解析运行期 datasourceType。
- G6/G7 已接入 FDL 数据流内可执行 Lookup / Filter：安全等值 Lookup 会生成独立 lookup `DBReadNode`，再通过 `SparkSqlNode` 与 base reader 做 LEFT JOIN；安全 Filter 在同一个 `SparkSqlNode` 中执行 WHERE。G7 进一步解析 `=`, `!=`, `<>` 和多条件 `AND`；非等值 Lookup 已生成 row_number 单行语义草案，但在 `Use Any Value` 行选择顺序证明前不标为可执行等价。
- G8/G9/G10 已接入 Workflow 条件 / Decision / 外部调用执行语义：`.Status` 与 `.Condition` 会结构化解析；严格 success-AND Decision 子集会用 FDL `ON_SUCCESS` 入线和 literal true 出分支执行化。
- G11/G15 已将 `WF_PRIP_Stage.XML` 的 Java/SOAP/API 调用保持为 `FDL_ADAPTER_REQUIRED`：行级调用条件字段已绑定到请求输入，HTTP status -> 输出字段映射已解析，并对 SOAP/POST + 简单行级条件子集生成 `row_api_adapter` Spark UDF 节点；运行期仍需验证 UDF provider 注册和调用。
- API 后置 Expression merge/projection 已接入 row API UDF adapter 口径：当 Expression 依赖可全部绑定到 source reader 字段和当前 API 输出端口时，生成 `source_reader -> row_api_adapter -> infa_api_post_expression -> writer`，不生成 `source_reader -> api_reader` 非法连线，也不使用恒真 join 升级为完整等价。
- G13 已接入 Target/session 写入策略生成：insert/update/upsert 会按 XML key fields 生成 FDL logical primary key 和 update strategy；writer truncate 会使用 FDL `TruncateWriteConf`；delete-only session 会在同连接且 key 可追溯时生成 `DELETE WHERE EXISTS(source SQL)`，否则生成 row-level delete adapter 待实现节点，避免误生成 DBWrite upsert。

## 功能翻译状态

| XML | 功能状态 | 目标字段行 | 同名映射 | LOW | MEDIUM | HIGH | 待翻译/待调研重点 |
|---|---|---:|---:|---:|---:|---:|---|
| [cc_lis_20260521.xml](cc_lis_20260521.xml.md) | FDL_TRANSLATION_READY | 377 | 100.0% | 377 | 0 | 0 | 同名透传 |
| [WF_HNC.XML](WF_HNC.XML.md) | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 315 | 100.0% | 0 | 315 | 0 | Expression x1 |
| [WF_PRIP_Stage.XML](WF_PRIP_Stage.XML.md) | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 7475 | 96.2% | 1659 | 5808 | 8 | Decision/status 子集已可执行；API adapter contract 和 OUT_ERRORINFO 后置投影已解析；Target 写入策略已生成，delete-only 需按同连接/key 条件走 SQL 或 adapter |
| [auditdb.xml](auditdb.xml.md) | XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET | 760 | 81.7% | 506 | 125 | 129 | 3 组等值 Lookup + Filter 已可执行；2 组非等值 Lookup 已有单行语义计划；未连接 Lookup 输入待翻译；ODBC/JDBC 按同名 FDL 数据连接解析 |

## 状态说明

- `FDL_TRANSLATION_READY`：当前 XML 功能语义可由已实现的 FDL 结构翻译覆盖。
- `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`：XML 语义已表达，插件需要继续实现对应 FDL 结构生成。
- `XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET`：部分转换已生成可执行 FDL 数据流，其余功能语义仍待翻译。
- `FDL_GENERATION_RESEARCH_PENDING`：XML 语义已表达，但需要继续调研 FDL 原生节点或插件 adapter 的配置方式。
- `XML_MISSING_RUNTIME_SECRET` / `XML_MISSING_EXTERNAL_CODE`：XML 本身缺少运行必需凭据或外部代码实现体。

## 下一步功能翻译优先级

1. 将 Java/SOAP/API 行级调用条件和 HTTP status 输出绑定落到 FDL 原生 API 配置或插件 adapter。
2. 将非等值 Lookup 的 SparkSQL 单行输出计划推进到可执行等价，证明 `Use Any Value` 的行选择/缓存顺序。
3. 继续解析未连接 Lookup INPUT/OUTPUT 端口，按 XML 端口默认值、NULL 或 Lookup 自身输出语义生成。
4. FDL 运行环境需存在与 XML `CONNECTIONREFERENCE` 同名的数据连接；转换器按连接名解析 datasourceType，不从 ODBC/JDBC_ODBC 字样推断。
5. 参数和变量按 Mapping/Workflow 作用域、默认值、持久化属性和引用位置迁移，不按变量名猜领域含义。
6. 对跨连接或 key 不可追溯的 delete-only session 补 row-level delete adapter。

## 明细文件

- `cc_lis_20260521.xml.csv` / `cc_lis_20260521.xml.md`
- `WF_HNC.XML.csv` / `WF_HNC.XML.md`
- `WF_PRIP_Stage.XML.csv` / `WF_PRIP_Stage.XML.md`
- `auditdb.xml.csv` / `auditdb.xml.md`
