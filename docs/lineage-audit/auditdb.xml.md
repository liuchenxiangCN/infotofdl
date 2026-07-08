# auditdb.xml 字段级 lineage 离线审计

- 功能翻译状态：**XML_SEMANTICS_PARSED_TRANSLATION_PENDING_WITH_EXECUTABLE_SUBSET**。XML 已表达功能语义，但当前插件仍有转换规则、FDL 结构拼装或产品适配待实现。
- Workflow/Worklet/Session/Mapping：10 / 0 / 22 / 22
- 目标字段审计行数：760
- 同名映射可覆盖：621 (81.7%)
- 离线等级分布：LOW 506，MEDIUM 125，HIGH 129

## G7 Filter / Lookup Procedure 审计

生成方式：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_filters_lookups.py /mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/auditdb.xml
```

审计脚本只读 XML，不连接数据库，不修改 FDL 运行目录，并对 password、token、cookie、license 等敏感值做脱敏。

G7-G11 插件实现后，`auditdb.xml` 中 Filter / Lookup Procedure 继续保持可见化，并把当前已可翻译的 Lookup / Filter 放入 FDL 数据流执行：

- `FieldLineageInfo` 会保存 Filter 条件、依赖字段、输出字段、目标字段、SQL WHERE 转换候选和限制原因。
- `FieldLineageInfo` 会保存 Lookup Procedure 输入字段、输出字段、Lookup SQL/表、连接、Lookup condition、目标字段和限制原因。
- 生成 FDL 时不再新增普通 `*_infa_semantics` 可视节点；Filter/Lookup/Expression/连接语义进入真实 `_query` 数据流节点备注、reader/writer 备注和目标字段注释。安全场景下 `_query` 会生成 `DBRead(base) + DBRead(lookup_*) -> SparkSqlNode(infa_lookup_filter) -> DBWrite`。
- 可转换的 Filter 会进入 Spark SQL `WHERE`；如果上游 Lookup 尚未翻译成可执行结构，则 Filter 不会因自身条件简单而被误标为可执行。
- Lookup 条件现在按谓词解析，支持 `=`、`!=`、`<>` 和多条件 `AND`。只有每个谓词都能配对一个 lookup 读字段和一个可追溯 SQL reader 输入字段时，才生成候选 join 谓词。
- 非等值 Lookup 已解析为 XML 功能语义，并已生成 Informatica 单行 Lookup 输出的 SparkSQL 草案；状态标记为 `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`，在 `Use Any Value` 的真实行选择/缓存顺序证明前不标为可执行等价。

专项审计汇总：`filters=5 lookups=6`。

| Mapping | 类型 | 条件/配置 | 目标字段 | 当前状态 |
|---|---|---|---:|---|
| `MP_STAGE_QD_LCCONT_SELF_NOTPAID` | Lookup | `AGENT_CODE = MONEY1 AND AGENT_CODE = TMAKE1 AND AGENT_CODE = ADDMONEYINFO1 AND AGENT_CODE = DEALTYPE1`，连接 `qd_214_uat_r`，有 Lookup SQL override，缓存 `YES`，多匹配策略 `Use Any Value` | 4 | `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`；需继续解析未连接 INPUT/OUTPUT 端口默认值、Lookup 输出或 NULL 语义 |
| `MP_STAGE_AML_FAILUREEND_TRANSACTION` | Filter | `isnull(RERATINGDATE)=false` | 20 | `FDL_DATAFLOW_EXECUTABLE`；Spark SQL 中执行 `RERATINGDATE IS NOT NULL` |
| `MP_STAGE_AML_FAILUREEND_TRANSACTION` | Lookup | `DEALNO = PLAN3_IN`，连接 `aml_171_uat_r`，有 Lookup SQL override | 3 | `FDL_DATAFLOW_EXECUTABLE`；`PLAN3_IN->PLAN3`，独立 lookup DBRead + Spark LEFT JOIN |
| `MP_STAGE_AML_RISKLEVEL_TIMELIMIT` | Filter | `isnull(FIANLRATINGDATE)=false` | 15 | `FDL_DATAFLOW_EXECUTABLE`；Spark SQL 中执行 `FIANLRATINGDATE IS NOT NULL` |
| `MP_STAGE_AML_RISKLEVEL_TIMELIMIT` | Lookup | `APPNTNO = APPNTNO_IN AND RELANO = PLAN1_IN`，连接 `aml_171_uat_r`，有 Lookup SQL override | 1 | `FDL_DATAFLOW_EXECUTABLE`；输入追溯到 `APPNTNO`、`PLAN1` |
| `MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` | Filter | `isnull(FIANLRATINGDATE)=false` | 14 | `FDL_DATAFLOW_EXECUTABLE`；Spark SQL 中执行 `FIANLRATINGDATE IS NOT NULL` |
| `MP_STAGE_AML_CUSTOMER_LEVELOVERDUE` | Lookup | `APPNTNO = APPNTNO_IN AND PLAN = PLAN_IN`，连接 `aml_171_uat_r`，有 Lookup SQL override | 2 | `FDL_DATAFLOW_EXECUTABLE`；输入追溯到 `APPNTNO`、`PLAN1` |
| `MP_STAGE_LIS_AML_SAMEAPP_QY` | Filter | `isnull(CONTNO_OTHER)=false` | 30 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；随上游非等值 Lookup 单行语义计划在 rn=1 后执行 WHERE |
| `MP_STAGE_LIS_AML_SAMEAPP_QY` | Lookup | `APPNTNO_OTHER = APPNTNO_IN AND OCCUPATIONCODE_OTHER = OCCUPATIONCODE_IN AND SALARY_OTHER != SALARY_IN`，连接 `lis_uat152`，有 Lookup SQL override，缓存 `YES`，多匹配策略 `Use Any Value` | 6 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；非等值 join 已解析并生成 row_number 单行草案，行选择顺序待证明后再标为可执行 |
| `MP_STAGE_LIS_AML_SAMEAPP_BQ` | Filter | `isnull(CONTNO_OTHER)=false` | 26 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；随上游非等值 Lookup 单行语义计划在 rn=1 后执行 WHERE |
| `MP_STAGE_LIS_AML_SAMEAPP_BQ` | Lookup | `APPNTNO_OTHER = APPNTNO_IN AND OCCUPATIONCODE_OTHER = PLAN_IN AND SALARY_OTHER != SALARY_IN`，连接 `lis_uat152`，有 Lookup SQL override，缓存 `YES`，多匹配策略 `Use Any Value` | 6 | `FDL_DATAFLOW_SINGLE_ROW_PLAN_READY`；非等值 join 已解析并生成 row_number 单行草案，行选择顺序待证明后再标为可执行 |

因此本文件的状态是“XML 功能语义已解析，部分 Lookup / Filter 已进入 FDL 可执行数据流，非等值 Lookup 已有单行语义计划，其余为插件翻译或 FDL 产品适配待实现”。ODBC/JDBC_ODBC 不再作为转换器能力缺口；转换器按连接名匹配 FDL 已有数据连接，未连接 Lookup 输入仍归为转换器能力待实现项。


## Target/Session 写入语义

| 写入策略 | Target 数 | 有 key 字段 Target 数 | 示例 |
|---|---:|---:|---|
| treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO | 21 | 20 | s_MP_STAGE_TRAIN_WITHOUT_TRAINING/STAGE_TRAIN_WITHOUT_TRAINING, s_MP_STAGE_TRAIN_ENTER_OVERTIME/STAGE_TRAIN_ENTER_OVERTIME, s_MP_STAGE_TRAIN_FALSE_TRAINING/STAGE_TRAIN_FALSE_TRAINING, s_MP_STAGE_TRAIN_WITHOUT_ANY_TRAIN/STAGE_TRAIN_WITHOUT_ANY_TRAINING, s_MP_STAGE_QD_LCCONT_SELF_NOTPAID/STAGE_QD_LCCONT_SELF_NOTPAID |
| treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=YES | 1 | 0 | s_MP_STAGE_AGENTCHNL_SYS_MENU_OPTION/STAGE_AGENTCHNL_SYS_MENU_OPTION1 |

- delete/update/upsert 写入不默认要求所有目标字段都有 CONNECTOR 入边；key 字段仍需生成 WHERE 条件来源。
- 每行 CSV 的 `write_semantics` 和 `target_key_field` 字段保留具体 Session/Target 写入属性。

## 转换类型

| 类型 | 数量 |
|---|---:|
| Expression | 11 |
| Filter | 5 |
| Lookup Procedure | 6 |
| Source Qualifier | 22 |

## 工作流条件功能翻译状态

- 未发现 Decision 节点或非空条件线。

## 连接适配状态

| 连接名 | 类型 | 子类型 | 用途 | FDL datasourceType | 状态 | 限制 |
|---|---|---|---|---|---|---|
| auditdb_99_uat_rw_sjods_user | Relational | Oracle | WRITER | oracle | SAFE |  |
| training_uat | Relational | ODBC | READER | runtime_connection_by_name | FDL_TRANSLATION_READY |  |
| lis_uat152 | Relational | Oracle | READER | oracle | SAFE |  |
| qd_214_uat_r | Relational | Oracle | LOOKUPEXTENSION | oracle | SAFE |  |
| qd_214_uat_r | Relational | Oracle | READER | oracle | SAFE |  |
| cem_214_uat_r | Relational | Oracle | READER | oracle | SAFE |  |
| aml_171_uat_r | Relational | Oracle | READER | oracle | SAFE |  |
| aml_171_uat_r | Relational | Oracle | LOOKUPEXTENSION | oracle | SAFE |  |
| agentchannel | Relational | ODBC | READER | runtime_connection_by_name | FDL_TRANSLATION_READY |  |
| lis_uat152 | Relational | Oracle | LOOKUPEXTENSION | oracle | SAFE |  |

## 同名映射覆盖情况

- 621 个字段当前按字段名可覆盖；其中 LOW 506、MEDIUM 12、HIGH 103。
- LOW 可视为已覆盖的同名透传；MEDIUM/HIGH 表示 XML 语义需要插件显式翻译，归为转换器功能待实现或待调研项。
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.COMCODE
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.T2NAME
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.T3NAME
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.BRANCHTYPE
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.BRANCHTYPENAME
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.AGENT_CODE
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.AGENT_NAME
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.AGENT_ID_NO
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.AGENT_START_DATE
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.AGENT_END_DATE
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.AGENT_FINAL_DATE
- LOW：s_MP_STAGE_TRAINS / - / s_MP_STAGE_TRAIN_WITHOUT_TRAINING / STAGE_TRAIN_WITHOUT_TRAINING.PERSON_NAME
- 其余 609 个见 CSV 明细。

## 功能翻译待实现或待调研字段

| 等级 | Workflow | Worklet | Session | Target | 同名匹配 | 功能翻译说明 |
|---|---|---|---|---|---|---|
| HIGH | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_TRAINING | STAGE_TRAIN_WITHOUT_TRAINING.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_ENTER_OVERTIME | STAGE_TRAIN_ENTER_OVERTIME.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_FALSE_TRAINING | STAGE_TRAIN_FALSE_TRAINING.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_ANY_TRAIN | STAGE_TRAIN_WITHOUT_ANY_TRAINING.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | - | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | STAGE_QD_LCCONT_SELF_NOTPAID.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | - | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | STAGE_QD_LCCONT_SELF_NOTPAID.MONEY | no | 路径经过 Lookup Procedure；G4 会显式展示输入/输出/目标字段和限制，仅在安全时生成 SQL join，复杂 Lookup 仍需继续生成 FDL 数据流结构。; Lookup condition: AGENT_CODE = MONEY1 AND AGENT_CODE = TMAKE1 AND AGENT_CODE = ADDMONEYINFO1 AND AGENT_CODE = DEALTYPE1; Lookup Sql Override: selec... |
| HIGH | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | - | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | STAGE_QD_LCCONT_SELF_NOTPAID.TMAKE | no | 路径经过 Lookup Procedure；G4 会显式展示输入/输出/目标字段和限制，仅在安全时生成 SQL join，复杂 Lookup 仍需继续生成 FDL 数据流结构。; Lookup condition: AGENT_CODE = MONEY1 AND AGENT_CODE = TMAKE1 AND AGENT_CODE = ADDMONEYINFO1 AND AGENT_CODE = DEALTYPE1; Lookup Sql Override: selec... |
| HIGH | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | - | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | STAGE_QD_LCCONT_SELF_NOTPAID.ADDMONEYINFO | no | 路径经过 Lookup Procedure；G4 会显式展示输入/输出/目标字段和限制，仅在安全时生成 SQL join，复杂 Lookup 仍需继续生成 FDL 数据流结构。; Lookup condition: AGENT_CODE = MONEY1 AND AGENT_CODE = TMAKE1 AND AGENT_CODE = ADDMONEYINFO1 AND AGENT_CODE = DEALTYPE1; Lookup Sql Override: selec... |
| HIGH | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | - | s_MP_STAGE_QD_LCCONT_SELF_NOTPAID | STAGE_QD_LCCONT_SELF_NOTPAID.DEALTYPE | no | 路径经过 Lookup Procedure；G4 会显式展示输入/输出/目标字段和限制，仅在安全时生成 SQL join，复杂 Lookup 仍需继续生成 FDL 数据流结构。; Lookup condition: AGENT_CODE = MONEY1 AND AGENT_CODE = TMAKE1 AND AGENT_CODE = ADDMONEYINFO1 AND AGENT_CODE = DEALTYPE1; Lookup Sql Override: selec... |
| HIGH | s_MP_STAGE_QD_CUSTOMER_RISKLEVEL_TIMELIMIT | - | s_MP_STAGE_QD_CUSTOMER_RISKLEVEL_TIMELIMIT | STAGE_QD_CUSTOMER_RISKLEVEL_TIMELIMIT.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_QD_AGENT_UNDISCHARGED | - | s_MP_STAGE_QD_AGENT_UNDISCHARGED | STAGE_QD_AGENT_UNDISCHARGED.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_CEM_VS | - | s_MP_STAGE_CEM_V_TOAUDITING_INVOICE | STAGE_CEM_V_TOAUDITING_INVOICE.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_CEM_VS | - | s_MP_STAGE_CEM_V_TOAUDITING_TRAVEL_PLANE | STAGE_CEM_V_TOAUDITING_TRAVEL_PLANE.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_CEM_VS | - | s_MP_STAGE_CEM_V_TOAUDITING_INVOICE_D | STAGE_CEM_V_TOAUDITING_INVOICE_DETAIL.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_CEM_VS | - | s_MP_STAGE_CEM_V_TOAUDITING_INVOICE_O | STAGE_CEM_V_TOAUDITING_INVOICE_OTHER.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_CEM_VS | - | s_MP_STAGE_CEM_V_TOAUDITING_TRAVEL_HOTEL | STAGE_CEM_V_TOAUDITING_TRAVEL_HOTEL.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_AMLS | - | s_MP_STAGE_AML_FAILUREEND_TRANSACTION | STAGE_AML_FAILUREEND_TRANSACTION.ID | no | 目标 key 字段没有 CONNECTOR 入边；delete/update 写入需要 key 字段生成 WHERE 条件。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| HIGH | s_MP_STAGE_AMLS | - | s_MP_STAGE_AML_FAILUREEND_TRANSACTION | STAGE_AML_FAILUREEND_TRANSACTION.MANAGECOM | yes | 路径经过 Filter；G4 会显式展示过滤条件并仅在安全时下推 SQL WHERE，复杂依赖仍需继续生成 FDL 过滤结构。; Filter Condition: isnull(RERATINGDATE)=false |
| HIGH | s_MP_STAGE_AMLS | - | s_MP_STAGE_AML_FAILUREEND_TRANSACTION | STAGE_AML_FAILUREEND_TRANSACTION.T2CODE | yes | 路径经过 Filter；G4 会显式展示过滤条件并仅在安全时下推 SQL WHERE，复杂依赖仍需继续生成 FDL 过滤结构。; Filter Condition: isnull(RERATINGDATE)=false |
| HIGH | s_MP_STAGE_AMLS | - | s_MP_STAGE_AML_FAILUREEND_TRANSACTION | STAGE_AML_FAILUREEND_TRANSACTION.T2NAME | yes | 路径经过 Filter；G4 会显式展示过滤条件并仅在安全时下推 SQL WHERE，复杂依赖仍需继续生成 FDL 过滤结构。; Filter Condition: isnull(RERATINGDATE)=false |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_TRAINING | STAGE_TRAIN_WITHOUT_TRAINING.OPERATOR | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_TRAINING | STAGE_TRAIN_WITHOUT_TRAINING.MAKE_DATE | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_TRAINING | STAGE_TRAIN_WITHOUT_TRAINING.MAKE_TIME | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_TRAINING | STAGE_TRAIN_WITHOUT_TRAINING.MODIFY_DATE | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_WITHOUT_TRAINING | STAGE_TRAIN_WITHOUT_TRAINING.MODIFY_TIME | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_ENTER_OVERTIME | STAGE_TRAIN_ENTER_OVERTIME.OPERATOR | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_ENTER_OVERTIME | STAGE_TRAIN_ENTER_OVERTIME.MAKE_DATE | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_ENTER_OVERTIME | STAGE_TRAIN_ENTER_OVERTIME.MAKE_TIME | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_ENTER_OVERTIME | STAGE_TRAIN_ENTER_OVERTIME.MODIFY_DATE | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |
| MEDIUM | s_MP_STAGE_TRAINS | - | s_MP_STAGE_TRAIN_ENTER_OVERTIME | STAGE_TRAIN_ENTER_OVERTIME.MODIFY_TIME | no | 目标非 key 字段没有 CONNECTOR 入边；当前 Session/Target 表达 update/upsert 写入，FDL 翻译应只对有来源字段生成 SET，并用 key 字段生成 WHERE。 写入语义：treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO, keys=ID。 |

## 文件

- CSV 明细：`auditdb.xml.csv`
