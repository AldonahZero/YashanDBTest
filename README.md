# YashanDBTest

Tinghua 上的 SQLancer / YashanDB 黑盒逻辑测试工程。

- 工程目录：`/root/YashanDBTest`
- Git origin：`git@github.com:AldonahZero/YashanDBTest.git`
- 数据库：官方允许学习、测试、适配的 Enterprise 评估下载包 **23.4.1.109**，并非已验证存在的 23.5 个人版。
- 数据库安装用户：`yashantest`；程序、数据、服务端日志位于 `/home/yashantest/`。
- 模式：`mysql`；监听 `127.0.0.1:1688`（原生）与 `127.0.0.1:1690`（MySQL）。
- SQLancer 上游：`ab328b756bf5d923b9d93959af3432546efe56fd`，详见 `docs/source-provenance.json`。
- Java 11；MySQL Connector/J 8.4.0，通过 `yashandb-mysql8` Maven profile 选择。

当前七天实验从 **2026-10-16 13:23:39 +08:00** 到 **2026-10-23 13:23:39 +08:00**，两个独立 TLP_WHERE 工作进程，各一个线程，不同 seed。systemd 托管，退出 SSH 不会终止。截止时间写入持久配置，服务重启不会重置七天计时。

## 登录、状态和日志

```bash
ssh Tinghua
cd /root/YashanDBTest

# 实时检查次数、seed、PID、运行目录和截止时间
python3 scripts/status.py

# 持续查看第一个工作进程的 SQLancer 进度日志，自动跟随每小时轮换
python3 scripts/logs.py --worker 0

# 第二个工作进程
python3 scripts/logs.py --worker 1

# 当前实际执行的 SQL
python3 scripts/logs.py --worker 0 --sql

# 调度器的启动、退出、重试事件
tail -f logs/tlp-7d.log

# 服务状态与数据库状态
systemctl status yashandbtest-tlp-7d --no-pager
python3 scripts/db_admin.py status
```

在日志查看命令中按 Ctrl+C 只退出查看，不会停止测试。详细 SQL 日志位于各次 `results/<run-id>/logs/yashandb/`，控制台为 `console.log`；运行前清单为 `manifest.json`，实时预言机计数为 `oracle-metrics.json`，结束统计为 `summary.json`。

停止整个七天实验：

```bash
systemctl stop yashandbtest-tlp-7d
```

数据库仍由自己的管理进程维护。需要单独停止本测试数据库时执行 `python3 scripts/db_admin.py stop`。不得把本工程的管理命令指向其他实例。

## 已完成的验证与适配边界

3 项结果比较单元测试、12 项固定数据契约检查，以及跨 10 个数据库状态的 10,000 次 TLP 检查通过。短测最终结果为 `results/20261016_131554_829092_yashandb_tlp_where/summary.json`；早期失败目录保留了适配诊断，不计为已确认数据库缺陷。

当前生成器支持 INT、NULL 数据、重复行、简单索引、UPDATE/DELETE、INNER/LEFT JOIN，以及有界算术、COALESCE、ABS、NULLIF、AND/OR/NOT 等确定性表达式。TLP 比较完整投影行的频数，保留重复次数与 NULL。

此构建拒绝对布尔谓词直接使用 `(p) IS NULL`。第三个分区采用 `CASE WHEN (p) THEN 0 WHEN NOT(p) THEN 0 ELSE 1 END = 1`。这仍然是 TLP 的未知分区，等价关系不变。另限制 NULLIF 首参数为已知 INT 列，避免全 NULL 常量推导出 VARCHAR。

NoREC 代码已实现，但本次七天实验仅运行 TLP。未启用自动 reducer；日志是候选复现材料，结果不一致仍需干净状态重放和最小化。没有内核源码插桩，不报告代码覆盖率。

## 构建

```bash
bash scripts/build.sh
```

长测期间不更换源码、JAR、驱动或数据库配置，以保持实验可比较。每轮记录 JAR 哈希、固定 seed 和版本。整个七天实验按一小时轮次保存；工作进程失败后保留该轮并以新 seed 继续。连续三轮在 100 次有效检查前失败，或剩余磁盘小于 20GiB 时，调度器保存状态并停止，标记需要处理。

安装包、数据库口令、MySQL 客户端凭据和运行产物均被 `.gitignore` 排除，口令文件权限为 600。默认不将日志或缺陷自动发布到 GitHub。

下载来源与许可入口：[官方公开下载中心](https://www.yashandb.com/download)、[软件许可协议](https://download.yashandb.com/agreement)。安装包 SHA-256：`4bf5f861033d42a8e120f117a37d3c4f1a5b91856d8ffe5fa04c944a1e864d46`，已验证官方 SHA-512。
