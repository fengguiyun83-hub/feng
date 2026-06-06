# AGENT.md

这是 Codex 的项目记忆文档。以后用户要求升级项目、修复问题、生成资源或进行重要排查时，都要把关键改动和当前进度记录到这里，方便对话中断后快速继续。

记录要求：

- 后续所有记录默认使用中文。
- 每次改动后，在“变更日志”中追加一条记录。
- 记录应包含：用户需求、完成内容、涉及文件、验证情况、遗留问题或下一步建议。
- 不要删除历史记录，除非用户明确要求整理或重写。

## 项目快照

- 项目名称：`robot-management-system`
- 工作目录：`D:\AI_Lab\my_project\robot_management_system`
- 构建工具：Maven Wrapper，Windows 下使用 `mvnw.cmd`
- Java 版本：17
- 主要框架：Spring Boot 3.3.2
- 流处理技术栈：Kafka + Apache Flink 1.19.1 + Redis Stream
- 当前形态：机器人遥测实时处理实验项目，还不是完整的机器人管理系统产品。

## 当前已实现功能

- Spring Boot 应用入口：
  - 文件：`src/main/java/com/example/robotmanagementsystem/RobotManagementSystemApplication.java`
  - 当前只负责启动一个空的 Spring Boot 应用，尚未包含控制器、业务服务、持久化或前端界面。
- 本地基础设施：
  - 文件：`docker-compose.yml`
  - 可启动 Kafka、Redis、Flink JobManager 和 Flink TaskManager。
  - `kafka-init` 服务会自动创建 Kafka 主题 `robot-telemetry`。
- 遥测数据模拟：
  - 文件：`src/test/java/com/example/robotmanagementsystem/RobotTelemetryKafkaProducerTest.java`
  - 向 Kafka 主题 `robot-telemetry` 发送高频机器人关节温度遥测 JSON 数据。
  - 当前模拟规模为 24 台机器人、每台 6 个关节、每台机器人 500 轮事件。
- 实时温度告警任务：
  - 文件：`src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
  - 从 Kafka 读取机器人遥测 JSON。
  - 解析 `robotId`、温度和时间戳字段。
  - 温度字段支持 `temperature` 或 `temperatureCelsius`。
  - 时间戳支持 Unix 秒、Unix 毫秒或 ISO-8601 字符串。
  - 按机器人维度计算 5 秒滑动事件时间窗口内的平均温度，窗口每 1 秒滑动一次。
  - 当平均温度超过配置阈值时产生告警，默认阈值为 `80.0`。
  - 告警会打印到控制台，并写入 Redis Stream `robot:alerts`。
- 构建输出：
  - `pom.xml` 同时配置了 Spring Boot jar 和 Flink shaded job jar。
  - 当前工作区中已有 `target/` 目录，里面包含之前构建出的 jar 文件。

## 已知待完善点

- 产品功能：
  - 增加真正的机器人管理 API，例如机器人注册、状态查询、遥测查询、指令下发、维护记录、告警确认等。
  - 增加前端或仪表盘，用于查看机器人状态、温度趋势和实时告警。
  - 增加持久化存储，用于保存机器人元数据、遥测摘要、告警历史和用户操作记录。
- 流处理与运维：
  - 补充启动 Docker 服务、发送示例遥测、提交 Flink 作业、读取 Redis 告警的文档化命令。
  - 增加更完整的 Flink 部署路径；当前 compose 文件只启动 Flink 集群，不会自动提交 job jar。
  - 增加 checkpoint、重启策略、背压说明和监控指标。
  - 将 Kafka、Redis、主题名、告警阈值和窗口参数通过配置文件或环境变量统一管理。
  - 对无效遥测增加死信处理或指标统计，而不是只打印到 stderr。
- 测试：
  - 当前 Kafka 生产者写在 JUnit 测试里，但行为更接近负载生成器，并且依赖本机 `localhost:9092` 上有 Kafka。
  - 增加 JSON 解析、时间戳解析、平均值聚合、告警过滤等单元测试。
  - 增加基于 Testcontainers 或本地 Docker 工作流的集成测试。
- 代码结构：
  - 如果 Flink 任务继续变大，可以把当前嵌套类拆分成更聚焦的独立文件。
  - 考虑用 Jackson 序列化告警 JSON，替代手写字符串格式化。
  - 增加机器人、关节、遥测事件、告警等领域模型。
- 仓库卫生：
  - `target/` 已被 `.gitignore` 忽略，但当前工作区里仍存在生成目录。
  - `dependency-reduced-pom.xml` 当前存在于工作区，看起来像 maven-shade-plugin 产生的构建产物，需要确认是否删除或加入忽略。
  - 当前 git 工作区已经有不少新增和修改文件，除非用户明确要求，不要回退任何已有改动。

## 快速接手步骤

1. 查看当前工作区状态：
   ```powershell
   git status --short
   ```
2. 阅读本文档“变更日志”中的最新记录。
3. 如果要处理后端或流处理逻辑，优先查看：
   - `pom.xml`
   - `docker-compose.yml`
   - `src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
4. 如果要处理遥测数据模拟，优先查看：
   - `src/test/java/com/example/robotmanagementsystem/RobotTelemetryKafkaProducerTest.java`
5. 运行 Kafka 生产者测试前，需要先通过 Docker Compose 启动 Kafka。

## 变更日志

### 2026-06-06 - 项目巡检并创建记忆文档

- 用户需求：
  - 浏览整个项目。
  - 说明项目当前功能和待完善的地方。
  - 新增名为 `AGENT.md` 的记忆文档，用于记录未来的项目升级和改动。
- 完成内容：
  - 巡检了项目根目录、Maven 配置、Docker Compose、Spring Boot 入口、Flink 作业、Kafka 生产者测试、`.gitignore`、`.mvn`、`.vscode` 和已有构建输出。
  - 新增了 `AGENT.md` 项目记忆文档。
- 当前判断：
  - 项目目前是一个分布式机器人遥测实时处理原型。
  - 已实现的最核心能力是通过 Kafka、Flink、Redis 完成实时高温告警。
  - 项目仍缺少完整机器人管理系统所需的 API、前端、数据库、机器人生命周期管理、告警处理流程和运维文档。
- 验证情况：
  - 本次为文档创建与项目巡检，没有运行代码或测试命令。

### 2026-06-06 - 将记忆文档转换为中文

- 用户需求：
  - 将记忆文档改为用中文记录。
  - 将现有记录内容转换为中文。
- 完成内容：
  - 将 `AGENT.md` 中的说明、项目快照、当前功能、待完善点、快速接手步骤和历史记录全部改写为中文。
  - 在文档开头明确后续记录默认使用中文。
- 涉及文件：
  - `AGENT.md`
- 验证情况：
  - 本次为文档内容转换，没有运行代码或测试命令。

