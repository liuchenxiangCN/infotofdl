# 0.5.7 SQL 空白与目标字段元数据回归修复

日期：2026-07-14

## 修复范围

1. 0.5.6 在按 Source Qualifier 端口顺序重写 SQL 投影别名时，裁掉了 `SELECT` 后与顶层 `FROM` 前的空白，可能生成 `select字段...别名from表`。0.5.7 只在 SQL 结构解析确认的顶层投影边界保留原空白，不按文本批量替换 `select` 或 `from`，因此不会误改包含这些文本的字段名、表名、字符串或注释。
2. 0.5.6 在普通数据同步任务中用 Informatica XML 目标字段元数据覆盖 FDL 在线刷新得到的目标表元数据，导致实际目标字段的精确类型、方言类型和备注与保存 DTO 不一致，打开任务时出现结构变化提示。0.5.7 仍由 `MAPPING/CONNECTOR` 决定字段关系，读取端类型仍使用 XML 已证明类型；写入端存在在线目标表元数据时使用 FDL 刷新值，仅在线元数据缺失时回退 XML。

## 回归覆盖

- 真实样例 `MP_STAGE_TRAIN_ENTER_OVERTIME` 的有效 SQL 保留 `select ` 和 ` AS COMCODE\r\n  from ` 边界。
- 单行、多行和 `UNION ALL` 投影改写均保留关键字分隔。
- `selected_from_value`、`from_archive` 等标识符不会被当成关键字替换。
- `writeColumn` 保留在线目标字段的 JDBC 类型、`dialectTypeName` 和备注；在线目标元数据不可用时仍可用 XML 类型生成。
- Connector 决定的读取字段身份和 XML 读取类型不受目标元数据策略调整影响。

## 验证边界

本版本已完成离线构建、JDK 8 DTO 探针和四份 Informatica XML 回归。未自动部署到 FDL 运行目录；UI 中应使用 0.5.7 新建任务验证，旧任务不会自动重写保存 DTO。
