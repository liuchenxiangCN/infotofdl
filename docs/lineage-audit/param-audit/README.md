# 参数/变量作用域离线审计

生成方式：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl
python3 scripts/audit_infa_params.py /mnt/d/项目文档/2-项目/百年人寿
```

本审计只读取 Informatica XML，不启动 FDL，不连接数据库，不修改 FDL 运行目录。

## 口径

- 只输出 XML 中声明的 `MAPPINGVARIABLE` / `WORKFLOWVARIABLE` 和属性值中的变量引用位置。
- 不按变量名猜 `sysdate`、业务日期、连接或环境含义。
- XML 有 `DEFAULTVALUE` 时按 XML 输出；空字符串显示为 `""`，缺失显示为 `XML_MISSING_PARAMETER_DEFAULT`。
- `$PM...`、`$Source`、`$Target`、`.Status/.Condition` 等运行期/内置引用标为 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`。
- `${...}` 和 `Parameter Filename` 标为 `PARAMETER_FILE_REFERENCE`，只作为外部参数文件绑定合同，不猜实际值。

## 输出文件

- `params.json`：稳定 JSON，包含变量声明、引用位置和参数文件属性。
- `params.csv`：变量/参数声明与 reference-only 运行期引用聚合。
- `param_references.csv`：每个引用位置一行，包含 Session/Mapping/Workflow 上下文、replacement contract 和 required binding。
- `parameter_files.csv`：`Parameter Filename` 属性位置、值和 replacement contract。

## XML 覆盖摘要

| XML | Mapping variables | Workflow variables | References | Resolved | Runtime pending | Unresolved | Ambiguous | Parameter file attrs | Non-empty parameter file |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| cc_lis_20260521.xml | 0 | 64 | 18 | 0 | 18 | 0 | 0 | 6 | 0 |
| WF_HNC.XML | 3 | 81 | 13 | 4 | 9 | 0 | 0 | 2 | 1 |
| WF_PRIP_Stage.XML | 476 | 2744 | 1944 | 317 | 1627 | 0 | 0 | 200 | 1 |
| auditdb.xml | 0 | 348 | 104 | 0 | 104 | 0 | 0 | 32 | 0 |

## 待 Java resolver 处理的引用类型

| XML | Reference | Status | Count |
|---|---|---|---:|
| WF_PRIP_Stage.XML | `$PMWorkflowName` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 398 |
| WF_PRIP_Stage.XML | `$PMSessionName` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 398 |
| WF_PRIP_Stage.XML | `$PMBadFileDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 221 |
| WF_PRIP_Stage.XML | `$PMSessionLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 199 |
| WF_PRIP_Stage.XML | `$Source` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 199 |
| WF_PRIP_Stage.XML | `$Target` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 199 |
| auditdb.xml | `$PMBadFileDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 22 |
| auditdb.xml | `$PMSessionLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 22 |
| auditdb.xml | `$Source` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 22 |
| auditdb.xml | `$Target` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 22 |
| auditdb.xml | `$PMWorkflowLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 10 |
| auditdb.xml | `$PMCacheDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 6 |
| cc_lis_20260521.xml | `$PMBadFileDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 4 |
| cc_lis_20260521.xml | `$PMSessionLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 4 |
| cc_lis_20260521.xml | `$Source` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 4 |
| cc_lis_20260521.xml | `$Target` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 4 |
| cc_lis_20260521.xml | `$PMWorkflowLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 2 |
| WF_HNC.XML | `$PMWorkflowName` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 2 |
| WF_HNC.XML | `$PMSessionName` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 2 |
| WF_HNC.XML | `$PMBadFileDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_HNC.XML | `$PMSessionLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_HNC.XML | `$Source` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_HNC.XML | `$Target` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_HNC.XML | `$PMWorkflowLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_CRM_Customer.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Agent.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Claim.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Cont.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Finance.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Pos.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Product.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_Public.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_Stage_RN.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_XQGL_Mission.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$WL_PRIP_EAST.Status` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$判定.Condition` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |
| WF_PRIP_Stage.XML | `$PMWorkflowLogDir` | XML_SEMANTICS_PARSED_TRANSLATION_PENDING | 1 |

## 非空 Parameter Filename

| XML | Scope | Value |
|---|---|---|
| WF_HNC.XML | WORKFLOW:WF_HNC | `/home/oracle/para_prip.txt` |
| WF_PRIP_Stage.XML | WORKFLOW:WF_PRIP_Stage | `/home/infa/para_prip.txt` |

## 后续生产 Java 接入点

1. 在 XML 解析层建立 `Mapping/Workflow/Worklet/Session` 作用域索引，按当前 Mapping/Session 解析 `$$...`，按 Workflow/Worklet 解析 `$Task.Status` / `$Decision.Condition`。
2. 在 FDL 节点生成前增加 scoped param resolver，把 XML 默认值、空默认值、运行期内置引用和外部 parameter file 明确区分。
3. 在条件线、SQL override、Expression、Filter、Lookup、API adapter contract 生成路径中统一调用 resolver，禁止按变量名猜业务含义。
