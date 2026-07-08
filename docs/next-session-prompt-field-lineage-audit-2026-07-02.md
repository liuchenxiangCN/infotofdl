# 下一对话提示词：字段级 Lineage 离线审计

把下面整段复制到新对话中使用。

```text
你现在接手 `/home/liufeng/code/bainianrenshou/infotofdl` 这个项目。请先阅读：

1. `/home/liufeng/code/bainianrenshou/infotofdl/AGENTS.md`
2. `/home/liufeng/code/bainianrenshou/infotofdl/docs/project-handoff-2026-07-02.md`
3. `/home/liufeng/code/bainianrenshou/infotofdl/docs/build-and-ui-smoke-test.md`
4. `/home/liufeng/code/bainianrenshou/infotofdl/docs/migration-coverage-audit-2026-07-02.md`

背景：

- 这是帆软 FDL 插件 `com.fr.plugin.tptj.ivan.jsd19083`，来源仓库是 `https://gitee.com/gongezh/infotofdl`。
- 目标是构建通用 Informatica XML 到 FDL 的功能转换器，按 XML 记录的任务语义生成 FDL 结构，不按业务含义确认字段、接口或变量。
- Informatica 调度迁移不在当前范围内。
- 用户不会审计代码，只会用 FDL UI 验证结果。因此每个阶段都要给出可运行产物、可观察结果、验证步骤和回退方式。

本地环境：

- 源码：`/home/liufeng/code/bainianrenshou/infotofdl`
- FDL：`/mnt/d/FDL-tomcat-win64`，Windows 路径 `D:\FDL-tomcat-win64`
- 样例 XML：`/mnt/d/项目文档/2-项目/百年人寿`
- FDL 自带 JDK8：`/mnt/d/FDL-tomcat-win64/jdk`
- WSL 当前没有全局 Linux `java` / `gradle`，不要假设这是 Gradle 工程。
- 不要保存、打印数据库密码、Token、Cookie、license。

当前已完成：

- `plugin-src/build.sh` 已能用本机 FDL 自带 JDK8 和运行库构建 baseline 插件包。
- 用户已通过 FDL UI 验证 `cc_lis_20260521.xml` 当前没问题。
- 用户已通过 FDL UI 验证 `WF_HNC.XML` 当前看起来也没问题。
- 本地 Oracle 验证表已准备：
  - `cc_lis_20260521.xml`：`CC_ANSWER_INFO_LIS` 114、`CC_CALLCENTERRESULT` 12、`CC_CALLCENTERRESULT1` 12、`CC_CUSTOMER_INFO_LIS` 108、`CC_NB_FEEDBACK_INFO` 143。
  - `WF_HNC.XML`：`LCCONT` 105、`LCINSURED` 2。
- `scripts/generate_infa_oracle_ddl.py` 已支持：
  - 从 XML Source/Target 字段生成本地 Oracle DDL。
  - 保留 Target 实例名，例如 `CC_CALLCENTERRESULT1`。
  - 识别 Source Qualifier SQL override 里的额外依赖表，例如 `LCINSURED`。

关键判断：

- 当前 UI 通过不能证明字段级 lineage 已完整迁移。
- 当前插件字段映射逻辑在 `SyncTaskNodeBean.createFieldTransferItems`，它依赖 FDL 预览 SQL origin 字段和目标表 target 字段后，按字段名忽略大小写匹配。
- 这只覆盖 SQL 输出列名和目标字段同名的场景。
- 对 SQL alias、Expression 改名、Expression 计算字段、Filter、Lookup Procedure、Java/API 响应字段，不能只靠 UI 判断功能翻译是否完整。

本轮目标：

请建立“字段级 lineage 离线审计”能力，先不要急着改插件生成逻辑。目标是把用户无法在 UI 里验证的字段映射问题转成可读报告。

具体工作：

1. 新增脚本，建议路径：
   `/home/liufeng/code/bainianrenshou/infotofdl/scripts/audit_infa_field_lineage.py`
2. 脚本读取 Informatica XML，至少支持这四个文件：
   - `cc_lis_20260521.xml`
   - `WF_HNC.XML`
   - `WF_PRIP_Stage.XML`
   - `auditdb.xml`
3. 解析内容至少包括：
   - Workflow、Worklet、Session、Mapping 名称。
   - Source、Source Qualifier、Expression、Filter、Lookup Procedure、Java Transformation、Target 实例。
   - `CONNECTOR` 链路，构建字段级有向图。
   - 每个 Target 字段的上游字段路径。
   - 路径上经过的转换类型。
   - 是否存在 Expression 表达式、Filter 条件、Lookup Procedure、Java/API、Decision/条件线等风险。
   - 当前插件“按字段名同名匹配”是否能覆盖该 Target 字段。
4. 输出报告目录，建议：
   `/home/liufeng/code/bainianrenshou/infotofdl/docs/lineage-audit/`
5. 输出文件建议包括：
   - 每个 XML 一个 Markdown 摘要报告。
   - 每个 XML 一个 CSV 明细，字段至少包含：
     `xml_file, workflow, worklet, session, mapping, target_instance, target_field, upstream_fields, path, transform_types, same_name_mapping, risk_level, notes`
   - 一个总览 Markdown：
     `docs/lineage-audit/README.md`
6. 报告要明确给出：
   - 哪些字段是安全的同名透传。
   - 哪些字段需要插件显式处理 lineage。
   - 哪些字段的 XML 语义已表达但插件尚未翻译，哪些字段需要继续调研 FDL 结构生成方式，哪些字段属于 XML 本身缺信息。
   - 下一步应该优先修哪个插件能力。

验收标准：

- 脚本能在 WSL 中直接运行，不依赖 FDL 启动。
- 能处理 XML 编码 GB18030/GBK/UTF-8。
- 对 `cc_lis_20260521.xml` 和 `WF_HNC.XML`，报告应能解释为什么 UI 看起来可用：多数目标字段应是同名透传或 SQL 输出名与目标名一致。
- 对 `WF_PRIP_Stage.XML`，报告应突出 Decision、Java SOAP、Expression、海量参数/字段链路风险。
- 对 `auditdb.xml`，报告应突出 Filter、Lookup Procedure、ODBC/连接类型风险。
- 不要修改 FDL 运行目录，不要部署插件，除非用户后续明确要求。
- 完成后更新：
  - `AGENTS.md`
  - `docs/project-handoff-2026-07-02.md`
  - 新增或更新 `docs/lineage-audit/README.md`

最终回复请用中文，简洁说明：

- 新增了哪些脚本和报告。
- 各 XML 的功能翻译状态。
- 下一步应该修插件的哪一块。
- 哪些内容用户需要通过 UI 验证，哪些内容属于离线审计结论。
```
