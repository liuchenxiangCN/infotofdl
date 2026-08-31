# 0.5.6 字段映射与 SQL 投影回归修复

日期：2026-07-13

## 范围

本版本处理用户问题 9—13 中可由 Informatica XML 离线证明的部分：

- 字段关系由 `MAPPING/CONNECTOR` 生成，不再把同名匹配作为 XML 映射权威。
- `readColumn` 类型取末端生产端口的 `TRANSFORMFIELD`，`writeColumn` 类型取目标 `TARGETFIELD`。
- XML 类型、精度、标度、可空和主键信息进入 FDL `SqlFieldBean`；在线预览只补充 XML 缺失类型。
- Source Qualifier 自定义 SQL 按已连接输出端口顺序生成唯一别名，覆盖无别名表达式、字面量、重复中文列名和 `UNION/UNION ALL`。
- `SELECT *` 保持运行时取元数据；SQL 投影少于已连接端口时只映射可证明前缀，缺失后缀保持未解决。
- `Lookup Procedure` 明确排除在外部 API 分类之外，真正的 Java/SOAP 识别保持不变。

## 证明边界

问题 12 分为两部分：

1. Lookup 被误生成虚拟/API 节点：已离线修复并覆盖全部 6 个 Lookup fixture。
2. `MP_STAGE_QD_LCCONT_SELF_NOTPAID` 的 Lookup 执行等价：XML 中仍存在未连接输入端口，保持 `XML_SEMANTICS_PARSED_TRANSLATION_PENDING`，未猜测业务值或宣称可执行等价。

本版本未部署到本地 FDL 运行目录。离线构建、真实 FDL DTO 类型探针和四 XML 回归通过，不等于客户现场运行验收。

## 回归 Gate

核心命令：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl/plugin-src
FDL_HOME=/mnt/d/FDL-tomcat-win64 bash build.sh

cd ..
python3 scripts/test_infa_field_mapping_types.py
bash scripts/test_infa_field_mapping_types.sh "/mnt/d/项目文档/2-项目/百年人寿"
python3 scripts/test_infa_fixture_baselines.py "/mnt/d/项目文档/2-项目/百年人寿"
python3 scripts/test_infa_problem_7_8_regression.py
bash scripts/test_fdl_native_conditions.sh
python3 scripts/check_no_sample_hardcoding.py
```

可执行 JDK 8 探针直接断言：

- XML 数值、字符、日期类型映射到非零 JDBC 类型。
- 在线预览与 XML 冲突时不会覆盖 XML 已证明类型。
- 只有 XML 缺少类型时才允许在线元数据补充。
- 最终 `FieldTransferBean` 序列化不存在已证明字段 `type=0`。
- 问题 10 的字面量、无别名表达式和缺失后缀状态符合预期。
- 问题 13 的重复中文 label 在每个 UNION 分支改为唯一端口别名。
- 问题 12 的 Lookup-only Mapping 不再生成 API 配置，Java/SOAP 正向 fixture 仍能识别。

## 运行时验收待办

部署验证需要用户单独授权。授权后按“备份活动插件 → 安装 0.5.6 → 重启 → 新建测试任务 → 检查保存 DTO/UI/预览 → 运行数据对账 → 复测问题 1—8 和 G13 → 必要时回退 0.5.5”执行。
