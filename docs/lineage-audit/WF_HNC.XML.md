# WF_HNC.XML 字段级 lineage 离线审计

- 功能翻译状态：**XML_SEMANTICS_PARSED_TRANSLATION_PENDING**。XML 已表达功能语义，但当前插件仍有转换规则、FDL 结构拼装或产品适配待实现。
- Workflow/Worklet/Session/Mapping：1 / 3 / 1 / 1
- 目标字段审计行数：315
- 同名映射可覆盖：315 (100.0%)
- 离线等级分布：LOW 0，MEDIUM 315，HIGH 0


## Target/Session 写入语义

| 写入策略 | Target 数 | 有 key 字段 Target 数 | 示例 |
|---|---:|---:|---|
| treat=Update, insert=YES, update=NO, updateAsInsert=NO, updateElseInsert=YES, delete=YES, truncate=NO | 1 | 1 | s_M_PRIP_Stage_LCCont/LCCONT |

- delete/update/upsert 写入不默认要求所有目标字段都有 CONNECTOR 入边；key 字段仍需生成 WHERE 条件来源。
- 每行 CSV 的 `write_semantics` 和 `target_key_field` 字段保留具体 Session/Target 写入属性。

## 转换类型

| 类型 | 数量 |
|---|---:|
| Expression | 1 |
| Source Qualifier | 1 |

## 工作流条件功能翻译状态

- 未发现 Decision 节点或非空条件线。

## 连接适配状态

| 连接名 | 类型 | 子类型 | 用途 | FDL datasourceType | 状态 | 限制 |
|---|---|---|---|---|---|---|
| prip_155 | Relational | Oracle | WRITER | oracle | SAFE |  |
| lis_uat152 | Relational | Oracle | READER | oracle | SAFE |  |

## 同名映射覆盖情况

- 315 个字段当前按字段名可覆盖；其中 LOW 0、MEDIUM 315、HIGH 0。
- LOW 可视为已覆盖的同名透传；MEDIUM/HIGH 表示 XML 语义需要插件显式翻译，归为转换器功能待实现或待调研项。
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.GRPCONTNO
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.CONTNO
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.PROPOSALCONTNO
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.PRTNO
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.CONTTYPE
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.FAMILYTYPE
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.FAMILYID
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.POLTYPE
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.CARDFLAG
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.MANAGECOM
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.EXECUTECOM
- MEDIUM：WF_HNC / WL_HNC / s_M_PRIP_Stage_LCCont / LCCONT.AGENTCOM
- 其余 303 个见 CSV 明细。

## 功能翻译待实现或待调研字段

| 等级 | Workflow | Worklet | Session | Target | 同名匹配 | 功能翻译说明 |
|---|---|---|---|---|---|---|
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.GRPCONTNO | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.CONTNO | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.PROPOSALCONTNO | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.PRTNO | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.CONTTYPE | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.FAMILYTYPE | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.FAMILYID | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.POLTYPE | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.CARDFLAG | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |
| MEDIUM | WF_HNC | WL_HNC | s_M_PRIP_Stage_LCCont | LCCONT.MANAGECOM | yes | 路径经过 Expression，但本次仅识别到同名透传；仍需插件显式识别透传语义。 |

## 文件

- CSV 明细：`WF_HNC.XML.csv`
