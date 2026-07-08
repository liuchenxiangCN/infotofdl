# WF_PRIP_Stage.XML Expression audit

- Expression fields: 5816
- Computed/non-pass-through fields: 1
- SQL pushdown supported: 0

## Kind counts

| kind | count |
|---|---:|
| CONDITIONAL | 1 |
| PASS_THROUGH | 5815 |

## Computed fields

| mapping | expression field | target | kind | deps | SQL pushdown | FDL dataflow | limitation |
|---|---|---|---|---|---|---|---|
| M_PRIP_ExtractData | EXPTRANS.OUT_ERRORINFO: DECODE(STATE,'0','informatic通知失败',ERRORINFO) | EXTRACTDATALOG.ERRORINFO | CONDITIONAL | STATE,ERRORINFO | no | FDL_ADAPTER_REQUIRED | depends on API adapter output generated through plugin Spark UDF row API adapter; this expression is handled by the FDL post-API projection dataflow, not source SQL pushdown; ru... |
