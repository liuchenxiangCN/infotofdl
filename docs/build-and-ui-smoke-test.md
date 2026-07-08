# 构建与 UI Smoke Test

日期：2026-07-02

## 当前构建状态

已跑通本地构建，生成插件包：

`/home/liufeng/code/bainianrenshou/infotofdl/plugin-src/fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.zip`

已复制一份到 Windows 桌面，方便 UI 选择：

`C:\Users\nifen\Desktop\FDL-plugin-builds\fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0-baseline-20260702.zip`

包结构已验证，包含：

- `plugin.xml`
- `fine-plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0.jar`
- `pluginkit-10.0-20231122.jar`

当前生成包是 baseline 构建包，还没有修复迁移语义缺口。它的价值是证明源码可以重新打包，为后续改造提供可回归基线。

## 可重复构建命令

在 WSL 中执行：

```bash
cd /home/liufeng/code/bainianrenshou/infotofdl/plugin-src
FDL_HOME=/mnt/d/FDL-tomcat-win64 bash build.sh
```

构建脚本会自动使用：

- JDK：`/mnt/d/FDL-tomcat-win64/jdk`
- 已安装插件基线：`/mnt/d/FDL-tomcat-win64/webapps/webroot/WEB-INF/plugins/plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0`
- FDL 运行库：`/mnt/d/FDL-tomcat-win64/webapps/webroot/WEB-INF/lib`
- Tomcat 运行库：`/mnt/d/FDL-tomcat-win64/lib`

## FDL 访问入口

本地 FDL HTTP 端口为 `8068`。

登录入口：

`http://localhost:8068/webroot/decision`

插件直接入口：

`http://localhost:8068/webroot/decision/plugin/jsd19083/html/index`

菜单入口：

管理系统中查找 `批量生成任务`。

## Smoke Test 文件

第一轮只用最简单的：

`D:\项目文档\2-项目\百年人寿\cc_lis_20260521.xml`

对应 WSL 路径：

`/mnt/d/项目文档/2-项目/百年人寿/cc_lis_20260521.xml`

## UI 操作步骤

1. 启动 FDL。
2. 打开 `http://localhost:8068/webroot/decision` 并登录。
3. 进入 `批量生成任务` 页面，或直接访问 `/webroot/decision/plugin/jsd19083/html/index`。
4. 点击 `选择文件`，上传 `cc_lis_20260521.xml`。
5. 不要选择 `来源数据连接` 和 `去向数据连接`。
6. 不要选择 `文件夹信息`，除非你想把 XML 的 Folder 统一放到某个一级目录下。
7. 点击 `执行`。
8. 等待 `执行结果` 表格显示。

重要说明：

- 来源/去向数据连接下拉是全局覆盖参数。为了验证 XML 原始迁移，第一轮不要填。
- 如果填了来源或去向连接，插件会用该连接覆盖所有 Session 中的连接，可能掩盖或制造问题。

## Smoke Test 预期

对 `cc_lis_20260521.xml`，预期 UI 上至少应看到：

- 上传成功。
- 执行结果为 `成功`。
- FDL 任务目录中生成 Folder `cc_lis`。
- Folder 下生成 2 个任务：
  - `wf_lis_to_callcenter_daily`
  - `wf_callcenter_to_lis_daily`
- 每个任务可以打开。
- 任务中应存在数据同步节点。
- 节点中的连接名应来自 XML：
  - `cc_133_uat_rw`
  - `lis_190_uat_newcrm_rw`

## 当前 Smoke Test 结果

2026-07-02 已由用户在本地 FDL UI 验证：

- `cc_lis_20260521.xml` 可以成功执行。
- 任务已成功创建。
- 初始验证时无法继续验证字段映射，因为本地 FDL 数据连接虽然已创建，但连接背后的测试库缺少 XML 中任务使用的表和字段。
- 补齐本地验证表后，用户已确认 `cc_lis` 当前没有问题。

## 本地 Oracle 验证表

2026-07-02 已针对 `cc_lis_20260521.xml` 生成并执行本地验证用 DDL。

生成脚本：

`/home/liufeng/code/bainianrenshou/infotofdl/scripts/generate_infa_oracle_ddl.py`

JDBC 执行器：

`/home/liufeng/code/bainianrenshou/infotofdl/scripts/OracleSqlRunner.java`

DDL 输出目录：

`/home/liufeng/code/bainianrenshou/infotofdl/docs/local-ddl/cc_lis_20260521`

已在本地 Oracle `jdbc:oracle:thin:@localhost:11521/XEPDB1` 的 `SYSTEM` schema 中确认以下表和字段数：

| 表名 | 字段数 |
|---|---:|
| `CC_ANSWER_INFO_LIS` | 114 |
| `CC_CALLCENTERRESULT` | 12 |
| `CC_CALLCENTERRESULT1` | 12 |
| `CC_CUSTOMER_INFO_LIS` | 108 |
| `CC_NB_FEEDBACK_INFO` | 143 |

执行过的校验文件：

`/home/liufeng/code/bainianrenshou/infotofdl/docs/local-ddl/cc_lis_20260521/verify_tables.sql`

说明：

- 两个 FDL 连接名 `cc_133_uat_rw`、`lis_190_uat_newcrm_rw` 如果都指向同一个本地 `SYSTEM` schema，同名表只会存在一份。
- `CC_CALLCENTERRESULT1` 是 Informatica Target 实例名，对应 Target Definition `CC_CALLCENTERRESULT`。FDL 当前会按实例名找目标表，因此本地验证库中需要同时存在 `CC_CALLCENTERRESULT` 和 `CC_CALLCENTERRESULT1`。
- 这批表只用于本地 UI 字段映射验证，不代表客户真实库结构需要变更。
- 可选文件 `cc_lis_20260521__optional_lis_package_stub.sql` 尚未执行。只有后续真正运行任务且遇到缺少 `PKG_newBusinessDealDetail.dealReturnResultInformatica` 时，才考虑在本地测试 schema 中创建这个 stub。

下一步 UI 验证：

1. 回到已生成的 `cc_lis` 任务目录。
2. 打开 `wf_lis_to_callcenter_daily`、`wf_callcenter_to_lis_daily`。
3. 逐个打开数据同步节点。
4. 刷新或查看源表、目标表字段。
5. 检查字段映射是否自动生成，是否存在明显缺字段、错字段、未映射字段。
6. 如果 UI 不刷新旧任务元数据，可以删除这次 smoke test 生成的任务后，重新上传 `cc_lis_20260521.xml` 再检查。

## 第二轮 Worklet 验证准备

第二轮建议验证：

`D:\项目文档\2-项目\百年人寿\WF_HNC.XML`

对应 WSL 路径：

`/mnt/d/项目文档/2-项目/百年人寿/WF_HNC.XML`

该 XML 用于验证 Worklet 和复用 Session 场景，结构为：

- 1 个 Workflow
- 3 个 Worklet
- 1 个 Session
- 1 个 Mapping
- 连接名：`prip_155`、`lis_uat152`

已生成并执行本地验证 DDL：

`/home/liufeng/code/bainianrenshou/infotofdl/docs/local-ddl/WF_HNC`

已在本地 Oracle `SYSTEM` schema 中确认：

| 表名 | 字段数 |
|---|---:|
| `LCCONT` | 105 |
| `LCINSURED` | 2 |

说明：

- `LCINSURED` 不在 XML 的 Source/Target Definition 中，而是在 `SQ_LCCONT` 的 Source Qualifier SQL override 里通过 `FROM lcinsured b` 引用。
- 该 SQL override 挂在 `lis_uat152` Source Qualifier 连接下，因此补充 DDL 文件为 `WF_HNC__lis_uat152_sql_dependencies.sql`。

下一步 UI 验证：

1. 在插件页面上传 `WF_HNC.XML`。
2. 不要选择 `来源数据连接` 和 `去向数据连接`，避免覆盖 XML 中的连接名。
3. 点击 `执行`。
4. 检查任务是否创建成功，Worklet 结构是否能打开。
5. 打开数据同步节点，确认 `LCCONT` 字段映射是否自动生成。

当前 UI 结果：

- 2026-07-02 用户确认 `WF_HNC.XML` 当前看起来没有问题。
- 该轮仍只能证明任务创建、打开和同名字段映射在 UI 上可用；无法证明 Informatica `CONNECTOR` 字段级 lineage 已完整转换。

## 当前验证边界

当前插件生成字段映射的方式是：

1. 将 Source Qualifier SQL 写入 FDL 读取节点。
2. 让 FDL 预览 SQL 结果字段和目标表字段。
3. 插件按字段名忽略大小写自动匹配。

这意味着：

- SQL 输出列名与目标字段同名时，UI 验证通常能通过。
- 如果存在 SQL alias、Expression 改名、Expression 计算字段、Filter、Lookup Procedure、Java/API 响应字段等情况，仅靠 UI 很难证明字段血缘和功能翻译已完整。
- 下一阶段需要先做离线字段级 lineage 审计，把 `CONNECTOR` 链路、目标字段来源、当前插件同名映射结果和风险点输出成报告。

## 失败时记录

如果 UI 显示失败，请记录：

- 上传文件名
- `执行结果` 表格中的错误信息
- FDL 日志中与 `jsd19083`、`DataSyncParser`、`saveWork failed` 相关的错误
- 是否已生成部分任务
- 生成的任务是否能打开

常用日志路径：

`D:\FDL-tomcat-win64\logs`

WSL 路径：

`/mnt/d/FDL-tomcat-win64/logs`

## 安装新包时的回退原则

当前 FDL 已安装 `0.3.0` 插件。后续如果要安装新构建包验证，应先备份：

`D:\FDL-tomcat-win64\webapps\webroot\WEB-INF\plugins\plugin-com.fr.plugin.tptj.ivan.jsd19083-0.3.0`

如果 FDL 插件管理界面拒绝安装同版本包，可以先不安装新包，直接用当前已安装插件完成 baseline smoke test。
