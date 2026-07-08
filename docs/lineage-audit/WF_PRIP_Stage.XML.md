# WF_PRIP_Stage.XML 字段级 lineage 离线审计

- 功能翻译状态：**XML_SEMANTICS_PARSED_TRANSLATION_PENDING**。XML 已表达功能语义，但当前插件仍有转换规则、FDL 结构拼装或产品适配待实现。
- Workflow/Worklet/Session/Mapping：1 / 12 / 199 / 199
- 目标字段审计行数：7475
- 同名映射可覆盖：7193 (96.2%)
- 离线等级分布：LOW 1659，MEDIUM 5808，HIGH 8


## Target/Session 写入语义

| 写入策略 | Target 数 | 有 key 字段 Target 数 | 示例 |
|---|---:|---:|---|
| treat=Update, insert=YES, update=NO, updateAsInsert=NO, updateElseInsert=YES, delete=YES, truncate=NO | 167 | 167 | s_M_PRIP_Stage_LBMission/LBMISSION, s_M_PRIP_Stage_LCCUWSub/LCCUWSUB, s_M_PRIP_Stage_LCSpec/LCSPEC, s_M_PRIP_Stage_LCTInsured/LCTINSURED1, s_M_PRIP_Stage_LCGrpReinsurePlan/LCGRPREINSUREPLAN |
| treat=Insert, insert=YES, update=NO, updateAsInsert=NO, updateElseInsert=NO, delete=NO, truncate=NO | 11 | 10 | s_M_PRIP_Stage_LCGet/LCGET, s_M_PRIP_Stage_LCSecondInsured/LCSECONDINSURED1, s_M_PRIP_QD_LD_Code/ld_code1, s_M_PRIP_Stage_LDSysvar/LDSYSVAR1, s_M_PRIP_Stage_LIRiskRate/LIRISKRATE |
| treat=Insert, insert=YES, update=NO, updateAsInsert=NO, updateElseInsert=NO, delete=NO, truncate=YES | 5 | 4 | s_M_PRIP_Stage_LCCont/LCCONT, s_M_PRIP_Stage_LPEdorPerson/LPEDORPERSON1, s_M_PRIP_Stage_LDCode1/LDCODE1, s_M_PRIP_Stage_LMDutyCtrl/LMDUTYCTRL1, s_M_PRIP_Stage_LMEdorItem/LMEDORITEM1 |
| treat=Insert, insert=YES, update=NO, updateAsInsert=NO, updateElseInsert=YES, delete=YES, truncate=NO | 1 | 1 | s_M_PRIP_Stage_LCInsured/LCINSURED_Insert |
| treat=Delete, insert=NO, update=NO, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO | 14 | 14 | s_M_PRIP_Stage_LCContState_DEL/LCCONTSTATE_DEL, s_M_PRIP_Stage_LCGet_DEL/LCGET_DEL, s_M_PRIP_Stage_LLBalance_DEL/LLBALANCE_DEL, s_M_PRIP_Stage_LCInsured_delete/LCINSURED, s_M_PRIP_Stage_LCBnf_DEL/LCBNF_DEL |
| treat=Insert, insert=YES, update=YES, updateAsInsert=NO, updateElseInsert=NO, delete=YES, truncate=NO | 1 | 1 | s_M_PRIP_ExtractData/EXTRACTDATALOG |

- delete/update/upsert 写入不默认要求所有目标字段都有 CONNECTOR 入边；key 字段仍需生成 WHERE 条件来源。
- 每行 CSV 的 `write_semantics` 和 `target_key_field` 字段保留具体 Session/Target 写入属性。

## 转换类型

| 类型 | 数量 |
|---|---:|
| Custom Transformation | 1 |
| Expression | 153 |
| Source Qualifier | 199 |

## 工作流条件功能翻译状态

- Decision 节点：判定
- 条件线：判定 -> s_M_PRIP_ExtractData，条件 `$判定.Condition = true`

## Java/SOAP/API 功能翻译状态

- G10/G11/G15 状态：插件已将 `M_PRIP_ExtractData` 中 Java Transformation `Java` 识别为 `FDL_ADAPTER_REQUIRED`。当前不生成原生 FDL API 数据流节点；符合 SOAP/POST + 简单行级条件 + HTTP status 输出绑定的场景会生成 `row_api_adapter` SparkSQL 节点并调用插件 UDF `infa_soap_http_status`，仍需 FDL runtime 验证 provider 注册和 UDF 调用。
- 离线审计：`python3 scripts/audit_infa_api_calls.py /mnt/c/Users/nifen/Desktop/项目文档/2-项目/百年人寿/WF_PRIP_Stage.XML`。
- 调用节点：`s_M_PRIP_ExtractData` / `M_PRIP_ExtractData` / `Java`。
- 请求：`POST http://20.12.2.153:8403/services/ExtractionPripDataService?wsdl`，SOAP body 动作为 `doExtractDate`，`SOAPAction` 来自 Java 调用第三参且值为空字符串。
- 请求字段：`STATE <= SQ_EXTRACTDATALOG.STATE [Source Qualifier]`。
- 响应/结果字段：`OUT_STATE -> STATE`，后续落 `EXTRACTDATALOG.STATE`。
- 已解析 HTTP 状态输出映射：`OUT_STATE <= 1 when HTTP status 200; default OUT_STATE <= 0; source method sendAndGetResponseData`。
- 已解析 adapter contract 证据：行级条件字段 `STATE` 已绑定到请求输入 `STATE <= SQ_EXTRACTDATALOG.STATE [Source Qualifier]`；状态码映射目标 `OUT_STATE` 是 Java/API 输出端口并映射到目标 `STATE`。
- 下游 Expression 结构证据：`EXPTRANS.OUT_ERRORINFO` 同时依赖 adapter 输出 `Java.OUT_STATE` 和源字段 `SQ_EXTRACTDATALOG.ERRORINFO`；当前生成 `row_api_adapter -> infa_api_post_expression`，在同一输入行内完成 API 状态输出和后置投影，不使用 `_fdl_adapter_row_id` join。
- FDL 生成边界：XML 中 API 调用语义已经解析，并已落成 Spark UDF row API adapter 结构；未经过 FDL runtime 部署验证前，不升级为 full GO。不生成 `source_reader -> api_reader` 非法连线，不使用恒真 join。

## 连接适配状态

| 连接名 | 类型 | 子类型 | 用途 | FDL datasourceType | 状态 | 限制 |
|---|---|---|---|---|---|---|
| prip_155 | Relational | Oracle | WRITER | oracle | SAFE |  |
| lis_uat152 | Relational | Oracle | READER | oracle | SAFE |  |
| qd_214_uat_r | Relational | Oracle | READER | oracle | SAFE |  |
| xqgl119 | Relational | Oracle | READER | oracle | SAFE |  |
| crm133 | Relational | Oracle | READER | oracle | SAFE |  |
| prip_155 | Relational | Oracle | READER | oracle | SAFE |  |
| east | Relational | Oracle | READER | oracle | SAFE |  |

## 同名映射覆盖情况

- 7193 个字段当前按字段名可覆盖；其中 LOW 1659、MEDIUM 5527、HIGH 7。
- LOW 可视为已覆盖的同名透传；MEDIUM/HIGH 表示 XML 语义需要插件显式翻译，归为转换器功能待实现或待调研项。
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.SERIALNO
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONID
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.SUBMISSIONID
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.PROCESSID
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.ACTIVITYID
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.ACTIVITYSTATUS
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONPROP1
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONPROP2
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONPROP3
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONPROP4
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONPROP5
- MEDIUM：WF_PRIP_Stage / WL_PRIP_Stage_Cont / s_M_PRIP_Stage_LBMission / LBMISSION.MISSIONPROP6
- 其余 7181 个见 CSV 明细。

## 功能翻译待实现或待调研字段

| 等级 | Workflow | Worklet | Session | Target | 同名匹配 | 功能翻译说明 |
|---|---|---|---|---|---|---|
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.SERIALNO | yes | Session 位于条件线后：$判定.Condition = true |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.BUSSINESSDATE | yes | Session 位于条件线后：$判定.Condition = true |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.MODULE | yes | Session 位于条件线后：$判定.Condition = true |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.CALCODE | yes | Session 位于条件线后：$判定.Condition = true |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.ERRORINFO | no | Session 位于条件线后：$判定.Condition = true; Expression EXPTRANS.OUT_ERRORINFO feeds ERRORINFO: DECODE(STATE,'0','informatic通知失败',ERRORINFO); deps=STATE,ERRORINFO; FDL API post-projection adapter required: source/API row-bound projection must be... |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.STATE | yes | Session 位于条件线后：$判定.Condition = true; 路径经过 Java/API Custom Transformation；XML 调用语义已解析。当前 SOAP/POST + 简单行级条件 + HTTP status 输出子集会生成 Spark UDF row API adapter，不生成 source_reader -> api_reader 非法连线；仍需 FDL runtime 验证 UDF provider 可注册、可调用。; Java... |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.MAKEDATE | yes | Session 位于条件线后：$判定.Condition = true |
| HIGH | WF_PRIP_Stage | - | s_M_PRIP_ExtractData | EXTRACTDATALOG.MAKETIME | yes | Session 位于条件线后：$判定.Condition = true |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.SERIALNO | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.MISSIONID | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.SUBMISSIONID | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.PROCESSID | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.ACTIVITYID | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.ACTIVITYSTATUS | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.MISSIONPROP1 | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.MISSIONPROP2 | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.MISSIONPROP3 | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_PRIP_Stage | WL_PRIP_Stage_Cont | s_M_PRIP_Stage_LBMission | LBMISSION.MISSIONPROP4 | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |

## 文件

- CSV 明细：`WF_PRIP_Stage.XML.csv`
