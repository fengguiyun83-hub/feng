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
- 构建工具：Maven；Windows 下可使用 `mvnw.cmd`，但当前环境中 wrapper 可能遇到用户目录 `.m2\wrapper` 权限问题。
- 当前可用 Maven 路径：`D:\Tools\apache-maven-3.9.9\bin\mvn.cmd`
- 当前推荐 Maven 参数：`"-Dmaven.repo.local=.m2/repository"`，用于避开用户目录 `.m2\repository` 权限问题。
- Java 版本：17
- 主要框架：Spring Boot 3.3.2
- 流处理技术栈：Kafka + Apache Flink 1.19.1 + Redis Stream
- 当前形态：Maven 多模块机器人管理系统原型，已经具备实时遥测告警链路、ROS 2 仿真桥接、后端 API、PostgreSQL 持久化、前端驾驶舱、2D 数字孪生和告警处理闭环。

## 当前已实现功能

- Maven 多模块结构：
  - 根 `pom.xml` 为 aggregator/parent。
  - `rms-common`：共享 DTO、Entity、枚举和 Repository。
  - `rms-flink-job`：纯 Flink/Kafka/Redis 作业模块，使用 Shade 输出 Flink job 胖包。
  - `rms-backend-api`：Spring Boot 后端 API、Service、Flyway migration 和测试。
- ROS 2 仿真桥接：
  - 文件：`ros2/rms_bridge_node`
  - 订阅 TurtleBot3 `/odom`，生成 RMS Telemetry v2 JSON，通过 `kafka-python` 写入 Kafka `robot-telemetry`。
  - 默认 `robotId=robot-001`，对齐 PostgreSQL `robots.robot_id` 字符串主键。
  - payload 同时携带 `rosStampMillis` 和 `bridgeSentAtMillis`，用于 WSL 2 时钟漂移排查。
- Spring Boot 应用入口：
  - 文件：`rms-backend-api/src/main/java/com/example/robotmanagementsystem/RobotManagementSystemApplication.java`
  - 当前提供 REST API、SSE 推送、PostgreSQL 持久化和告警生命周期处理。
- 后端 API 雏形：
  - 包：`com.example.robotmanagementsystem.api`
  - 提供机器人列表、机器人详情、机器人告警、告警列表、告警确认关闭、总览摘要、系统状态和 SSE 接口。
  - 当前不做登录权限，不做真实命令下发。
- 本地基础设施：
  - 文件：`docker-compose.yml`
  - 可启动 Kafka、Redis、PostgreSQL、Flink JobManager 和 Flink TaskManager。
  - `kafka-init` 服务会自动创建 Kafka 主题 `robot-telemetry`。
- 遥测数据模拟：
  - 文件：`rms-backend-api/src/test/java/com/example/robotmanagementsystem/RobotTelemetryKafkaProducerTest.java`
  - 早期 legacy Kafka 压测入口，会向 Kafka 主题 `robot-telemetry` 发送机器人关节温度遥测 JSON 数据。
  - 当前模拟规模为 24 台机器人、每台 6 个关节、每台机器人 500 轮事件，共 72,000 条消息。
  - 第十轮后该测试已标记为 `@Disabled`，仅保留为 deprecated/manual-only 历史工具，正常验收不再运行。
- 实时温度告警任务：
  - 文件：`rms-flink-job/src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
  - 从 Kafka 读取机器人遥测 JSON。
  - 解析 `robotId`、温度和时间戳字段。
  - 温度字段支持 `temperature` 或 `temperatureCelsius`。
  - 时间戳支持 Unix 秒、Unix 毫秒或 ISO-8601 字符串。
  - 按机器人维度计算 5 秒滑动事件时间窗口内的平均温度，窗口每 1 秒滑动一次。
  - 当平均温度超过配置阈值时产生告警，默认阈值为 `80.0`。
  - 告警会打印到控制台，并写入 Redis Stream `robot:alerts`。
  - 第八轮后会将实时状态写入 Redis Stream `robot:states`，并按机器人限流到约 5Hz。
  - 第十轮后默认使用 `--telemetry.required-source ros2-gazebo`，只处理 ROS 2/Gazebo 真数据，避免 `source=unknown` mock 数据污染状态和告警。
  - Flink 解析侧对 WSL 2 clock drift 做防抖，过旧或过新的时间戳会回退到处理时间。
- 构建输出：
  - `rms-flink-job/target/rms-flink-job-0.0.1-SNAPSHOT.jar`
  - `rms-backend-api/target/rms-backend-api-0.0.1-SNAPSHOT.jar`

## 第一轮基础整理后的运行流程

1. 启动基础设施：
   ```powershell
   docker compose up -d
   docker compose ps
   ```

2. 构建项目和 Flink job jar：
   ```powershell
   D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" clean package -DskipTests
   ```

3. 将 Flink job jar 复制到 JobManager 容器：
   ```powershell
   docker exec robot-flink-jobmanager mkdir -p /opt/flink/usrlib
   docker cp rms-flink-job\target\rms-flink-job-0.0.1-SNAPSHOT.jar robot-flink-jobmanager:/opt/flink/usrlib/robot-job.jar
   ```

4. 提交 Flink 任务：
   ```powershell
   docker exec robot-flink-jobmanager flink run -d /opt/flink/usrlib/robot-job.jar --bootstrap.servers kafka:19092 --group.id robot-ros2-only-flink-v1 --redis.host redis --alert.threshold 40 --redis.state-stream robot:states --state.emit-interval-ms 200 --telemetry.required-source ros2-gazebo
   ```

   说明：当前推荐使用新的 `robot-ros2-only-flink-v1` consumer group，并通过 `--telemetry.required-source ros2-gazebo` 避开旧 mock 积压数据。`--state.emit-interval-ms 200` 会将 ROS 2 状态流降频到约 5Hz。

5. 启动 ROS 2 bridge，而不是运行 legacy Kafka 遥测生产测试：
   - 在 WSL 2 中启动 `rms_bridge_node`，让 `robot-001` 到 `robot-005` 以 `source=ros2-gazebo` 写入 Kafka。
   - 如 Redis 里已有旧状态，先运行 `powershell -ExecutionPolicy Bypass -File .\scripts\clear-ros-state.ps1` 清理 `robot:states`。

6. 查看 Redis 告警：
   ```powershell
   docker exec robot-redis redis-cli XLEN robot:alerts
   docker exec robot-redis redis-cli XREVRANGE robot:alerts + - COUNT 5
   ```

7. 查看 Flink 任务状态：
   ```powershell
   docker exec robot-flink-jobmanager flink list
   ```

8. 启动后端 API：
   ```powershell
   D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl rms-backend-api -am spring-boot:run
   ```

9. 调用后端 API：
   ```powershell
   Invoke-RestMethod http://localhost:8080/api/robots
   Invoke-RestMethod http://localhost:8080/api/dashboard/summary
   Invoke-RestMethod http://localhost:8080/api/system/status
   ```

## 已知待完善点

- 产品功能：
  - 增加真正的机器人注册/编辑、遥测历史查询、命令下发、维护记录、批量告警处理和审计日志。
  - 继续优化前端驾驶舱，用于查看机器人状态、温度趋势、实时告警、处理流程和系统运行状态。
  - 增加遥测摘要或时序数据存储，用于趋势和统计分析。
- 流处理与运维：
  - 将 Kafka、Redis、topic、告警阈值和窗口参数统一配置化。
  - 增加 Flink checkpoint、重启策略、背压说明和监控指标。
  - 增加无效遥测的死信处理或指标统计，而不只是打印到 stderr。
  - 当前 compose 只启动 Flink 集群，不会自动提交 job jar。
- 测试：
  - 当前 Kafka 生产者写在 JUnit 测试中，但行为更接近负载生成器，并依赖本机 `localhost:9092` 上有 Kafka。
  - 后续应增加 JSON 解析、时间戳解析、平均值聚合、告警过滤等单元测试。
  - 后续可增加基于 Testcontainers 或本地 Docker 工作流的集成测试。
- 代码结构：
  - 多模块拆分已完成；如果 Flink 任务继续变大，可把当前嵌套类拆分成更聚焦的独立文件。
  - 考虑使用 Jackson 序列化告警 JSON，替代手写字符串格式化。
  - 增加机器人、关节、遥测事件、告警等领域模型。
- 仓库卫生：
  - `.gitignore` 已忽略 `target/`、`.m2/`、`dependency-reduced-pom.xml` 等生成物。
  - 当前工作区里仍可能存在本地生成目录和文件，不要在未确认前删除。
  - 当前 git 工作区已有多项新增和修改，避免回退任何用户已有改动。

## 后续迭代建议

1. 第七轮后下一步：告警处理增强
   - 增加批量确认、批量关闭、处理记录时间线和处理人选择。
2. 后续：机器人管理增强
   - 增加机器人注册、编辑、维护记录和命令下发。
3. 后续：遥测历史分析
   - 保存遥测摘要或时序数据，提供趋势和统计接口。
4. 后续：权限与审计
   - 增加登录、角色权限、操作审计和系统监控。

## 历史迭代概览

1. 第二轮：后端 API 雏形
   - 已完成 Spring Web、机器人列表、机器人详情、告警列表、系统状态接口。
   - 第一版数据来自内存 mock 和 Redis 查询。
2. 第三轮：前端驾驶舱雏形
   - 已新增 React + TypeScript + Ant Design 前端。
   - 已实现总览、机器人列表、告警中心和系统状态页。
   - 前端通过 Vite proxy 接后端 API。
3. 第四轮：数据库持久化
   - 引入 PostgreSQL。
   - 保存机器人档案、告警历史、命令记录。
4. 第五轮：真实管理能力增强
   - 增加命令下发、维护记录、告警确认关闭、审计日志和权限控制。

## 快速接手步骤

1. 查看当前工作区状态：
   ```powershell
   git status --short
   ```
2. 阅读 `README.md` 和本文件最新变更日志。
3. 如果处理后端或流处理逻辑，优先查看：
   - `pom.xml`
   - `docker-compose.yml`
   - `rms-backend-api/src/main/java/com/example/robotmanagementsystem`
   - `rms-flink-job/src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
4. 如果处理遥测数据模拟，优先查看：
   - `rms-backend-api/src/test/java/com/example/robotmanagementsystem/RobotTelemetryKafkaProducerTest.java`
5. 运行 Kafka 生产者测试前，需要先通过 Docker Compose 启动 Kafka。

## 变更日志

### 2026-06-06 - 项目巡检并创建记忆文档

- 用户需求：
  - 浏览整个项目。
  - 说明项目当前功能和待完善的地方。
  - 新增名为 `AGENT.md` 的记忆文档，用于记录未来的项目升级和改动。
- 完成内容：
  - 巡检项目根目录、Maven 配置、Docker Compose、Spring Boot 入口、Flink 作业、Kafka 生产者测试、`.gitignore`、`.mvn`、`.vscode` 和已有构建输出。
  - 新增 `AGENT.md` 项目记忆文档。
- 当前判断：
  - 项目目前是一个分布式机器人遥测实时处理原型。
  - 已实现的核心能力是通过 Kafka、Flink、Redis 完成实时高温告警。
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

### 2026-06-06 - 实际执行端到端验收流程

- 用户需求：
  - 按测试与验收方案实际运行项目，确认是否达到预期效果。
- 完成内容：
  - 启动 Docker Compose 中的 Kafka、Redis、Flink JobManager 和 Flink TaskManager。
  - 使用本机 Maven 路径完成构建并生成 Flink job jar。
  - 提交 Flink 任务。
  - 运行 Kafka 遥测生产测试，发送 72,000 条 telemetry events。
  - 查询 Redis Stream `robot:alerts`，确认链路在阈值 `40` 下能产生告警。
- 验证情况：
  - Maven 构建成功。
  - JUnit 测试结果：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
  - Flink 任务处于 `RUNNING`。
  - Redis Stream 曾查询到 1,159 条告警记录。
- 遗留问题：
  - `mvnw.cmd` 在当前环境中遇到用户目录 `.m2\wrapper` 权限问题。
  - `--alert.threshold 60` 对当前模拟数据偏高，不适合作为稳定演示阈值。

### 2026-06-06 - 第一轮项目基础整理

- 用户需求：
  - 不一次性完成全部系统，而是逐一慢慢改善优化。
  - 第一轮先整理项目基础，修复文档、固化运行流程和验收方式。
- 完成内容：
  - 修复 `AGENT.md` 中文乱码问题，重建为可读中文项目记忆。
  - 明确当前项目定位、已实现功能、运行流程、已知问题和后续迭代顺序。
  - 记录本地 Maven 路径、Flink 提交命令、Kafka 生产者测试命令和 Redis 告警查询命令。
- 涉及文件：
  - `AGENT.md`
  - `README.md`
- 验证情况：
  - 本轮只改文档，不改业务逻辑。
  - 后续可通过 README 中的命令重新运行端到端验收。

### 2026-06-06 - 第二轮后端 API 雏形

- 用户需求：
  - 将空 Spring Boot 应用升级为可供前端调用的后端服务。
  - 不引入数据库、不做真实权限、不改 Flink 链路。
- 完成内容：
  - 在 `pom.xml` 中新增 `spring-boot-starter-web`。
  - 新增 `api`、`model`、`service` 分层。
  - 新增机器人 mock 数据服务，固定生成 `robot-001` 到 `robot-024` 共 24 台机器人。
  - 新增 Redis Stream 告警读取服务，读取 `robot:alerts`，Redis 不可用时返回空列表或降级状态。
  - 新增接口：`/api/robots`、`/api/robots/{robotId}`、`/api/robots/{robotId}/alerts`、`/api/alerts`、`/api/dashboard/summary`、`/api/system/status`。
  - 新增 `RobotApiControllerTest`，覆盖机器人列表、机器人详情、404 和 dashboard summary。
  - 更新 `README.md`，补充后端 API 启动命令、接口列表和测试命令。
- 涉及文件：
  - `pom.xml`
  - `src/main/java/com/example/robotmanagementsystem/api/*`
  - `src/main/java/com/example/robotmanagementsystem/model/*`
  - `src/main/java/com/example/robotmanagementsystem/service/*`
  - `src/test/java/com/example/robotmanagementsystem/RobotApiControllerTest.java`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 使用项目内 Maven 仓库运行：`D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=RobotApiControllerTest test`
  - 结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- 遗留问题：
  - 还没有数据库持久化，机器人数据仍为 mock。
  - Kafka/Flink 系统状态暂为配置级说明，尚未做真实探测。
  - Redis 告警只读最近记录，尚未支持确认、关闭和备注。

### 2026-06-07 - 第三轮启动与验收脚本

- 用户需求：
  - 先不做前端，把本地启动、API 验证和端到端验收流程做稳。
  - 避免后台进程管理，解决此前 Spring Boot 手动启动验证不顺的问题。
- 完成内容：
  - 新增 `scripts/start-api.ps1`，以前台方式启动 Spring Boot API。
  - 新增 `scripts/verify-api.ps1`，验证 `/api/system/status`、`/api/robots` 和 `/api/dashboard/summary`。
  - 新增 `scripts/verify-stream.ps1`，检查 Docker Compose、Flink job、Redis Stream 长度和最近告警。
  - 新增 `scripts/run-api-tests.ps1`，运行 `RobotApiControllerTest`。
  - 更新 `README.md`，补充推荐开发工作流和脚本使用方式。
- 涉及文件：
  - `scripts/start-api.ps1`
  - `scripts/verify-api.ps1`
  - `scripts/verify-stream.ps1`
  - `scripts/run-api-tests.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 本轮脚本面向 Windows PowerShell。
  - 如果当前 PowerShell 执行策略禁止 `.ps1`，使用 `powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1`。
  - 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1`，结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
  - 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\verify-stream.ps1`，确认 Docker 服务可查询、Flink job 处于 `RUNNING`、Redis Stream `robot:alerts` 当前有 3822 条记录。
  - `verify-api.ps1` 需要先在独立终端运行 `start-api.ps1`。
- 遗留问题：
  - 脚本不自动启动或停止 Docker、Java 后台进程，避免误杀用户正在使用的服务。
  - 前端驾驶舱仍留到后续迭代。

### 2026-06-07 - 第四轮前端驾驶舱雏形

- 用户需求：
  - 开始执行下一步，做前端驾驶舱雏形。
  - 页面先接现有后端 API，便于后续边运行边调整设计。
- 完成内容：
  - 新增 `frontend/` 前端应用。
  - 使用 React、TypeScript、Vite、Ant Design、ECharts 和 Lucide React。
  - 实现总览、机器人列表、告警中心、系统状态四个视图。
  - 总览页展示机器人总数、在线数、Redis 告警数、平均温度、温度分布和最近告警。
  - 机器人列表支持状态筛选和关键词搜索。
  - 告警中心展示 Redis Stream 最近告警。
  - 系统页展示后端、Redis、Kafka、Flink 状态。
  - 更新 `README.md`，补充前端安装、启动、构建说明。
  - 更新 `.gitignore`，忽略 `frontend/node_modules/`、`frontend/dist/` 等前端生成物。
- 涉及文件：
  - `.gitignore`
  - `frontend/package.json`
  - `frontend/package-lock.json`
  - `frontend/index.html`
  - `frontend/tsconfig.json`
  - `frontend/tsconfig.node.json`
  - `frontend/vite.config.ts`
  - `frontend/src/*`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 运行 `npm.cmd run build` 成功。
  - 构建产物生成在 `frontend/dist/`，该目录已忽略。
  - Vite 提示 chunk 大小超过 500 kB，原因是第一版同时引入 Ant Design 和 ECharts，不影响运行，后续可通过按需加载优化。
- 遗留问题：
  - 前端还没有登录权限、机器人详情页、告警确认/关闭操作和命令下发页面。
  - npm install 曾出现超时，但依赖已安装并可构建；已生成 `package-lock.json` 固定版本。

### 2026-06-07 - 修复 Maven/IDE 诊断问题

- 用户需求：
  - 分析 IDE 报出的 Maven 和类路径诊断，并直接修改项目。
- 完成内容：
  - 曾尝试删除 `pom.xml` 中冗余的 `jedis.version` 属性和 Jedis 依赖版本声明，改由 Spring Boot dependency management 管理版本。
  - 曾尝试新增 `.mvn/maven.config` 和 `.mvn/settings.xml`，让 Maven/IDE 使用项目内 `.m2/repository`。
  - 曾尝试更新 `.vscode/settings.json`，为 Java/Maven 导入配置项目内 Maven 仓库、项目 settings 文件和本机 Maven 路径。
  - 更新 `README.md` 和本文件中的 Maven 命令说明。
- 涉及文件：
  - `pom.xml`
  - `.vscode/settings.json`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd -Dtest=RobotApiControllerTest test`。
  - 结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`，构建成功。
  - 去掉 Jedis 显式版本后，Spring Boot 3.3.2 实际管理的 Jedis 版本解析为 `5.0.2`，已下载到项目内 `.m2/repository`。
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd -s .mvn\settings.xml -q -DskipTests compile`，编译成功。
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd -s .mvn\settings.xml -Dtest=RobotApiControllerTest test`，结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- IDE 后续操作：
  - 在 VS Code 命令面板执行 `Java: Clean Java Language Server Workspace`。
  - 然后执行 `Maven: Reload All Maven Projects`。
  - 如果仍有旧诊断，重启 VS Code 后再重新导入 Maven 项目。

### 2026-06-07 - 回退 Maven/IDE 诊断修复尝试

- 用户需求：
  - 上一轮 IDE 报错数量增加，要求回退相关修改。
- 完成内容：
  - 恢复 `pom.xml` 中的 `jedis.version` 属性和 Jedis 显式版本声明。
  - 恢复 `.vscode/settings.json` 为简单 Java/Maven 自动导入配置。
  - 删除 `.mvn/maven.config` 和 `.mvn/settings.xml`。
  - 恢复 `README.md` 和本文件中 Maven 命令为显式携带 `"-Dmaven.repo.local=.m2/repository"` 的形式。
- 涉及文件：
  - `pom.xml`
  - `.vscode/settings.json`
  - `.mvn/maven.config`
  - `.mvn/settings.xml`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -Dtest=RobotApiControllerTest test`。
  - 结果：`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`，构建成功。

### 2026-06-07 - 第五轮驾驶舱体验优化和稳健 SSE

- 用户需求：
  - 在现有前端基础上优化驾驶舱体验。
  - 新增机器人详情抽屉。
  - 引入 SSE 实时推送，并重点处理后端僵尸连接清理、线程安全广播、前端断线状态和手动重连。
- 完成内容：
  - 新增后端 SSE 接口 `GET /api/stream/dashboard`，返回 `text/event-stream`。
  - 新增 `DashboardStreamService`，使用 `CopyOnWriteArrayList<SseEmitter>` 管理连接。
  - 为每个 emitter 注册 `onCompletion`、`onTimeout`、`onError`，统一移除连接。
  - 使用单线程 `ScheduledExecutorService` 每 5 秒广播 dashboard 快照，推送失败时清理坏连接。
  - 新增 `DashboardStreamSnapshot`，包含 `summary`、`alerts`、`system`、`timestamp`。
  - 前端拆分为 `views/`、`components/`、`hooks/` 等结构。
  - 新增 `useDashboardData`，统一处理 REST 初始化、手动刷新、SSE 更新、连接异常、30 秒断线降级和手动重连。
  - 新增 `RobotDetailDrawer`，机器人列表点击行后展示机器人基础信息、状态、电量、温度、最近告警和短时趋势。
  - 更新 `scripts/run-api-tests.ps1`，同时运行 `RobotApiControllerTest` 和 `DashboardStreamControllerTest`。
  - 更新 `README.md`，补充 SSE 接口、前端连接策略和第五轮验证命令。
- 涉及文件：
  - `src/main/java/com/example/robotmanagementsystem/api/DashboardStreamController.java`
  - `src/main/java/com/example/robotmanagementsystem/model/DashboardStreamSnapshot.java`
  - `src/main/java/com/example/robotmanagementsystem/service/DashboardStreamService.java`
  - `src/test/java/com/example/robotmanagementsystem/DashboardStreamControllerTest.java`
  - `frontend/src/App.tsx`
  - `frontend/src/hooks/useDashboardData.ts`
  - `frontend/src/components/ConnectionStatus.tsx`
  - `frontend/src/components/RobotDetailDrawer.tsx`
  - `frontend/src/views/*`
  - `scripts/run-api-tests.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" "-Dtest=RobotApiControllerTest,DashboardStreamControllerTest" test`。
  - 结果：`Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。
  - 已运行 `cd frontend; npm.cmd run build`。
  - 结果：TypeScript 和 Vite 构建成功。
  - Vite 仍提示 chunk 大于 500 kB，原因是 Ant Design 和 ECharts 集中打包，不影响本轮功能运行。
- 遗留问题：
  - 本轮未引入数据库、登录权限、告警确认关闭和命令下发。
  - SSE 目前推送 dashboard 快照，不承担命令通道。
  - 后续可优化前端代码拆包，降低首屏 JS 体积。

### 2026-06-07 - 第六轮 PostgreSQL 持久化地基

- 用户需求：
  - 引入 PostgreSQL 持久化，范围锁定为机器人档案和告警历史。
  - 使用 Spring Data JPA。
  - Kafka/Flink 仍只写 Redis Stream，后端负责把 Redis 最近告警同步进 PostgreSQL。
- 完成内容：
  - `docker-compose.yml` 新增 `robot-postgres`，默认数据库 `robot_management`，用户和密码均为 `robot`。
  - `pom.xml` 新增 Spring Data JPA、PostgreSQL、Flyway 和 H2 测试依赖。
  - 新增 `application.yml` 和测试用 H2 配置。
  - 新增 Flyway 迁移，创建 `robots` 和 `robot_alerts` 表。
  - 新增 JPA Entity 和 Repository 层。
  - `RobotService` 改为从数据库读取机器人档案。
  - 新增 `RobotSeedService`，空库启动时自动写入 `robot-001` 到 `robot-024`，并修复旧 mock 中文乱码。
  - 新增 `AlertPersistenceService`，从 Redis 最近告警幂等同步到 PostgreSQL，并从数据库返回告警列表。
  - `/api/alerts`、`/api/robots/{robotId}/alerts`、dashboard summary 和 SSE snapshot 改为通过持久化服务读取告警。
  - `/api/system/status` 增加 PostgreSQL 状态探测。
  - `scripts/run-api-tests.ps1` 纳入 API、SSE、机器人持久化和告警持久化测试。
  - `scripts/verify-stream.ps1` 增加 PostgreSQL readiness 检查。
- 涉及文件：
  - `pom.xml`
  - `docker-compose.yml`
  - `src/main/resources/application.yml`
  - `src/main/resources/db/migration/V1__create_robot_management_tables.sql`
  - `src/main/java/com/example/robotmanagementsystem/persistence/*`
  - `src/main/java/com/example/robotmanagementsystem/service/*`
  - `src/test/resources/application.yml`
  - `src/test/java/com/example/robotmanagementsystem/RobotPersistenceTest.java`
  - `src/test/java/com/example/robotmanagementsystem/AlertPersistenceServiceTest.java`
  - `scripts/run-api-tests.ps1`
  - `scripts/verify-stream.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 首次运行 Maven 测试时因沙箱网络限制无法下载新依赖，提升权限后已成功下载。
  - 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1`。
  - 结果：`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`。
  - 已运行 `cd frontend; npm.cmd run build`，构建成功，仍有 Vite 大 chunk 提醒。
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" clean package -DskipTests`，打包成功。
  - 已运行 `docker compose up -d`，新增 `robot-postgres` 正常启动并 healthy。
  - 已运行 `powershell -ExecutionPolicy Bypass -File .\scripts\verify-stream.ps1`，Kafka、Redis、Flink、PostgreSQL 查询通过，Flink job 处于 `RUNNING`，Redis `robot:alerts` 当前 3822 条。
  - 曾因本机 8080 端口已被占用，使用临时端口 `18080` 启动 API 完成 PostgreSQL 初始化。
  - PostgreSQL 验收结果：`robots` 表 24 条，`robot_alerts` 表在调用 `/api/alerts?limit=20` 后为 20 条。
- 遗留问题：
  - API 默认仍使用 8080；如果端口被占用，需要停止占用进程或临时指定 `--server.port=18080`。
  - 告警同步目前是读取接口触发的轻量同步，不是后台持续同步任务。
  - 由于后端 API 和 Flink job 仍在同一个 Maven 模块中，shade 打包会输出较多依赖重叠 warning；当前构建成功，后续可考虑拆分模块优化。
  - 还没有告警确认、关闭、备注、命令下发、权限和审计功能。

### 2026-06-08 - 第七轮 Maven 多模块重构和告警生命周期

- 用户需求：
  - 解决第六轮暴露的构建痛点，把单 Maven 模块重构为 `rms-common`、`rms-flink-job`、`rms-backend-api`。
  - 在后端和前端落地告警处理闭环，支持 `NEW -> ACKNOWLEDGED -> CLOSED`。
  - 未处理告警数只统计 `NEW`，确认和关闭后总览动态扣减。
- 完成内容：
  - 根 `pom.xml` 改为 aggregator/parent，集中管理版本、插件和模块列表。
  - 新增 `rms-common`，放共享 DTO、Entity、枚举和 Repository。
  - 新增 `rms-flink-job`，只保留 Flink/Kafka/Redis 流处理依赖，并用 Shade 输出 `rms-flink-job-0.0.1-SNAPSHOT.jar`。
  - 新增 `rms-backend-api`，放 Spring Boot 应用、Controller、Service、配置、Flyway migration 和测试。
  - 新增 `AlertStatus`：`NEW`、`ACKNOWLEDGED`、`CLOSED`。
  - `robot_alerts` 增加生命周期字段：`status`、`acknowledged_at`、`acknowledged_by`、`closed_at`、`closed_by`、`operation_note`、`updated_at`。
  - 新增 Flyway `V2__add_alert_lifecycle_columns.sql`，旧数据默认 `NEW`。
  - `AlertPersistenceService` 增加状态筛选、确认、关闭逻辑；Redis 重复同步不会覆盖已处理状态和备注。
  - 新增或更新接口：`GET /api/alerts?status=NEW`、`POST /api/alerts/{alertId}/acknowledge`、`POST /api/alerts/{alertId}/close`。
  - `DashboardSummary` 增加 `openAlertCount`，只统计 `NEW` 告警。
  - 前端告警中心增加状态筛选、状态列、确认告警、关闭告警和运维备注弹窗。
  - 前端总览使用 `openAlertCount` 展示未处理告警。
  - 更新 `scripts/start-api.ps1` 和 `scripts/run-api-tests.ps1`，适配 `rms-backend-api` 模块。
  - 更新 `README.md`，补充多模块结构、新 jar 路径、后端启动命令、生命周期 API 和验证命令。
- 涉及文件：
  - `pom.xml`
  - `rms-common/pom.xml`
  - `rms-common/src/main/java/com/example/robotmanagementsystem/model/*`
  - `rms-common/src/main/java/com/example/robotmanagementsystem/persistence/*`
  - `rms-flink-job/pom.xml`
  - `rms-flink-job/src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
  - `rms-backend-api/pom.xml`
  - `rms-backend-api/src/main/java/com/example/robotmanagementsystem/api/*`
  - `rms-backend-api/src/main/java/com/example/robotmanagementsystem/service/*`
  - `rms-backend-api/src/main/resources/db/migration/*`
  - `rms-backend-api/src/test/java/com/example/robotmanagementsystem/*`
  - `frontend/src/types.ts`
  - `frontend/src/api.ts`
  - `frontend/src/hooks/useDashboardData.ts`
  - `frontend/src/views/DashboardView.tsx`
  - `frontend/src/views/AlertCenterView.tsx`
  - `frontend/src/App.tsx`
  - `scripts/start-api.ps1`
  - `scripts/run-api-tests.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行后端模块测试：
    `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl rms-backend-api -am "-Dtest=RobotApiControllerTest,DashboardStreamControllerTest,RobotPersistenceTest,AlertPersistenceServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - 结果：`Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`。
  - 已运行根目录多模块打包：
    `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" clean package -DskipTests`
  - 结果：root、`rms-common`、`rms-flink-job`、`rms-backend-api` 均构建成功。
  - 已运行 `cd frontend; npm.cmd run build`，TypeScript 和 Vite 构建成功，仍有大 chunk 提醒，不影响运行。
- 遗留问题：
  - 告警处理目前没有登录鉴权，`operator` 仍由前端传默认值。
  - 告警处理记录目前保存在 `operation_note` 当前值中，还没有完整时间线表。
  - 命令下发、维护记录、权限控制、审计日志和遥测历史分析仍留到后续迭代。

### 2026-06-08 - 验收脚本误报修复

- 用户需求：
  - 修复 `scripts/verify-stream.ps1` 容易误报的问题。
  - Redis Stream 查询仍作为核心验收，Docker Compose 和 Flink 状态改为辅助展示。
  - PostgreSQL 不再作为 Kafka/Flink/Redis 流链路脚本的强制检查项。
- 完成内容：
  - `verify-stream.ps1` 新增 optional/required 两类命令执行逻辑。
  - `docker compose ps` 和 `flink list` 失败时只输出 `[WARN]`，不直接中断脚本。
  - `docker exec robot-redis redis-cli XLEN robot:alerts` 和 `XREVRANGE` 失败时输出 `[FAIL]` 并返回失败码。
  - `robot:alerts` 数量为 `0` 时输出 `[WARN]` 和生成告警的下一步建议，不再视为脚本执行失败。
  - `README.md` 补充 `verify-stream.ps1` 的职责边界：流链路验收归它，PostgreSQL/API 验证归 `verify-api.ps1` 或后端测试。
- 涉及文件：
  - `scripts/verify-stream.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-stream.ps1`。
  - 脚本语法正常，Docker Compose 和 Flink 辅助检查失败时已按预期输出 `[WARN]`，没有提前中断。
  - 当前环境 Docker Desktop named pipe 返回 `Access is denied` / `permission denied`，Redis 核心检查按预期输出 `[FAIL]` 并返回失败码。
  - 该失败属于当前 Docker 权限或 Docker Desktop 运行状态问题，不是脚本语法问题。

### 2026-06-09 - 第八轮 ROS 2 仿真桥接和 2D 数字孪生

- 用户需求：
  - 告别 24 台纯 mock 机器人数据，接轨 WSL 2 内 ROS 2 仿真生态。
  - 新增 Python `rms_bridge_node`，订阅 `/odom` 并将位姿数据写入 Kafka `robot-telemetry`。
  - Flink 状态流写 Redis `robot:states` 时必须限流到约 5Hz，避免 `/odom` 30Hz+ 冲垮 Redis 和后端 PostgreSQL 同步。
  - `robotId="robot-001"` 必须与 PostgreSQL `robots.robot_id` 主键保持一致。
  - WSL 2 存在 clock drift 风险，需要在 Python/Flink 侧做时钟容错。
- 完成内容：
  - 新增 `ros2/rms_bridge_node` ROS 2 Python package。
  - 桥接节点订阅 ROS 2 里程计话题，通过 `kafka-python` 发送 RMS Telemetry v2 JSON。
  - Telemetry v2 包含 `schemaVersion`、`source`、`robotId`、`timestamp`、`rosStampMillis`、`bridgeSentAtMillis`、位姿、速度、电量、电机电流和温度字段。
  - 默认 `robotId=robot-001`，直接对齐现有 PostgreSQL 字符串主键，不做自增 ID 转换。
  - Flink parser 兼容旧 telemetry 和 ROS v2 telemetry。
  - Flink 新增 Redis `robot:states` sink，并使用 keyed state 按 `robotId` 限流，默认 `--state.emit-interval-ms 200`。
  - Flink 解析侧增加时钟防抖：时间戳过旧 10 分钟以上或超前 2 分钟以上时回退到处理时间。
  - PostgreSQL `robots` 表新增 V3 migration，保存位姿、速度、电机电流和 telemetry source。
  - `RobotSnapshot` 增加数字孪生字段。
  - 新增 `RedisRobotStateService` 和 `RobotStateSyncService`，从 Redis `robot:states` 同步最新状态到 `robots` 表。
  - SSE snapshot 增加 `robots`，前端可实时接收机器人位置变化。
  - `/api/system/status` 增加 `ros2_bridge` 组件状态：`NO_DATA`、`UP`、`STALE`。
  - 前端新增“孪生”页，使用 ECharts 2D 坐标画布展示机器人位置、朝向、速度、电量、电机电流和 telemetry source。
  - 更新 `verify-stream.ps1`，增加 `robot:states` 检查。
  - 更新 `verify-api.ps1`，输出 `ros2_bridge` 状态和 `robot-001` 位姿。
  - 更新 `README.md`，补充 WSL 2 ROS 2 桥接节点构建、启动和验收流程。
- 涉及文件：
  - `ros2/rms_bridge_node/*`
  - `rms-flink-job/src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
  - `rms-flink-job/src/test/java/com/example/robotmanagementsystem/flink/RobotTelemetryJsonParserTest.java`
  - `rms-common/src/main/java/com/example/robotmanagementsystem/model/RobotSnapshot.java`
  - `rms-common/src/main/java/com/example/robotmanagementsystem/model/RobotTelemetryState.java`
  - `rms-common/src/main/java/com/example/robotmanagementsystem/persistence/RobotEntity.java`
  - `rms-backend-api/src/main/resources/db/migration/V3__add_robot_digital_twin_state.sql`
  - `rms-backend-api/src/main/java/com/example/robotmanagementsystem/service/RedisRobotStateService.java`
  - `rms-backend-api/src/main/java/com/example/robotmanagementsystem/service/RobotStateSyncService.java`
  - `frontend/src/views/DigitalTwinView.tsx`
  - `frontend/src/App.tsx`
  - `frontend/src/hooks/useDashboardData.ts`
  - `scripts/verify-api.ps1`
  - `scripts/verify-stream.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl rms-flink-job,rms-backend-api -am test`。
  - 结果：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`。
  - 该命令顺带运行了 `RobotTelemetryKafkaProducerTest`，成功发送 `72,000` 条 telemetry events 到 Kafka。
  - 已运行 `cd frontend; npm.cmd run build`，TypeScript 和 Vite 构建成功，仍有大 chunk 提醒，不影响运行。
- 遗留问题：
  - 尚未在真实 WSL 2 + Gazebo 环境中做人工联调。
  - 前端本轮采用 2D ECharts 孪生视图，Three.js 3D 小车模型留到后续迭代。
  - 当前 ROS bridge 默认只驱动 `robot-001`，多机器人 namespace 映射后续再扩展。

### 2026-06-10 - 第九轮 ROS 2 智能仓库世界和 5 台虚拟机器人集群

- 用户需求：
  - 在 ROS 2/Gazebo 中搭建精美的虚拟工厂或仓库环境。
  - 同时放入 3 到 5 台虚拟机器人，最终选择 5 台。
  - 交由现有 RMS 对 `robot-001` 到 `robot-005` 进行管理和监测。
- 完成内容：
  - 扩展 `rms_bridge_node`，新增 `robot_mappings` 参数，支持一次订阅多路机器人里程计话题。
  - 保留单机器人 `robot_id` / `odom_topic` 参数兼容模式。
  - 新增 `ros2/rms_warehouse_sim` package。
  - 新增 `rms_smart_warehouse.world`，包含仓库地面、墙体、货架、主通道、装卸区、发货区和充电区。
  - 新增 `rms_warehouse_5robots.launch.py`，启动 Gazebo world 并生成 `robot-001` 到 `robot-005` 五台虚拟机器人。
  - 新增 `rms_patrol_node`，为 5 台机器人发布巡航速度命令，按保守巡航状态机自动移动。
  - 前端“孪生”页增加仓库区域背景、5 台机器人不同颜色、`ros2-gazebo` 来源标签。
  - `verify-api.ps1` 输出 `robot-001` 到 `robot-005` 的坐标和 telemetry source。
  - `verify-stream.ps1` 增加最近 20 条 `robot:states` 样本提示，预期包含 5 个 robotId。
  - `README.md` 增加第九轮 WSL 构建、仓库 world、多机器人桥接、巡航节点和验收命令。
- 涉及文件：
  - `ros2/rms_bridge_node/rms_bridge_node/bridge_node.py`
  - `ros2/rms_bridge_node/rms_bridge_node/telemetry.py`
  - `ros2/rms_bridge_node/test/test_telemetry.py`
  - `ros2/rms_warehouse_sim/*`
  - `frontend/src/views/DigitalTwinView.tsx`
  - `scripts/verify-api.ps1`
  - `scripts/verify-stream.ps1`
  - `README.md`
  - `AGENT.md`
- 验证计划：
  - 运行 Python 单元测试验证 `robot_mappings` 和巡航逻辑。
  - 运行 Maven 回归确认 Flink/后端不回退。
  - 运行前端 build 确认孪生页不破坏 TypeScript/Vite 构建。
  - WSL 2 + Gazebo 人工联调仍需在用户本机 ROS 环境中执行。
- 遗留问题：
  - 当前巡航是脚本巡航，不是 Nav2 路径规划。

### 2026-06-10 - 第十轮 ROS-only 实时数据链路和 mock 污染清理

- 用户需求：
  - 当前系统同时收到 legacy mock 遥测和 ROS 2 真数据，导致 `source=unknown`、`positionX/Y=0` 的旧数据覆盖数字孪生真实位姿。
  - 用户明确表示现在不再需要 mock 数据，只要 ROS 2 连接的数据。
- 完成内容：
  - Flink 新增 `--telemetry.required-source` 参数，默认 `ros2-gazebo`，状态流和告警流只处理 ROS 2/Gazebo 真数据。
  - 后端新增 `robot.telemetry.required-source=ros2-gazebo` 配置，`RobotStateSyncService` 只同步符合来源的 Redis state，并在 robotId 去重前过滤旧数据。
  - legacy `RobotTelemetryKafkaProducerTest` 标记为 `@Disabled`，保留源码但不再参与默认测试或验收。
  - 新增 `scripts/clear-ros-state.ps1`，用于清理 Redis `robot:states`，可选清理 `robot:alerts`，不删除 Kafka topic 或 PostgreSQL 档案。
  - `verify-api.ps1` 默认要求 `robot-001` 到 `robot-005` 的 `telemetrySource` 为 `ros2-gazebo`，可用 `-AllowMissingRos2` 做基础 API 检查。
  - `verify-stream.ps1` 输出最近 `robot:states` 的 robotId 和 source 统计，发现 `unknown` 会提示清理和重提 ROS-only Flink job。
  - `README.md` 更新为 ROS-only 主流程，旧 72,000 条 mock 生产测试改为 deprecated/manual-only 说明。
- 涉及文件：
  - `rms-flink-job/src/main/java/com/example/robotmanagementsystem/flink/RobotTemperatureAlertFlinkJob.java`
  - `rms-flink-job/src/test/java/com/example/robotmanagementsystem/flink/RobotTelemetryJsonParserTest.java`
  - `rms-backend-api/src/main/java/com/example/robotmanagementsystem/service/RobotStateSyncService.java`
  - `rms-backend-api/src/test/java/com/example/robotmanagementsystem/RobotStateSyncServiceTest.java`
  - `rms-backend-api/src/test/java/com/example/robotmanagementsystem/RobotTelemetryKafkaProducerTest.java`
  - `scripts/clear-ros-state.ps1`
  - `scripts/verify-api.ps1`
  - `scripts/verify-stream.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl rms-flink-job,rms-backend-api -am test`。
  - 结果：`Tests run: 20, Failures: 0, Errors: 0, Skipped: 1`，其中 skipped 为 deprecated/manual-only 的 `RobotTelemetryKafkaProducerTest`。
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" clean package -DskipTests`，构建成功。
  - 已用 PowerShell Parser 检查 `scripts/clear-ros-state.ps1`、`verify-api.ps1`、`verify-stream.ps1`、`run-api-tests.ps1`、`start-api.ps1`，语法通过。
- 下一步建议：
  - 停止旧 Flink job，运行 `scripts/clear-ros-state.ps1`，使用 `--group.id robot-ros2-only-flink-v1 --telemetry.required-source ros2-gazebo` 重提 Flink job。
  - Gazebo world 使用基础几何体搭建，后续可替换为更精细 mesh 或 Gazebo Sim 资源。
  - 多机器人桥接仍写同一个 Kafka topic，符合当前 RMS 链路；后续如果接真实机器人，可再引入边缘网关和认证。

### 2026-06-10 - 修复 ROS 2 Jazzy / Gazebo Sim Harmonic 兼容性

- 用户反馈：
  - WSL 2 当前环境是 Ubuntu 24.04 + ROS 2 Jazzy，启动第九轮仓库仿真时报错 `package 'gazebo_ros' not found`。
  - 根因是原实现沿用了 Gazebo Classic / Gazebo 11 的 `gazebo_ros` 和 `turtlebot3_gazebo`。
- 完成内容：
  - `rms_warehouse_sim` 依赖从 `gazebo_ros`、`turtlebot3_gazebo` 切换为 `ros_gz_sim`、`ros_gz_bridge`。
  - 新增自包含 `models/rms_agv` Gazebo Sim 差速 AGV 模型，不再依赖 TurtleBot3 Classic 模型资源。
  - `rms_warehouse_5robots.launch.py` 改用 `ros_gz_sim/launch/gz_sim.launch.py` 启动 world，并使用 `ros_gz_sim create` 生成 5 台 AGV。
  - 每台机器人启动 `ros_gz_bridge parameter_bridge`，桥接 `/model/robot_x/odometry` 和 `/model/robot_x/cmd_vel`。
  - `rms_patrol_node` 巡航命令话题切换为 `/model/{robotId}/cmd_vel`。
  - README 更新 Jazzy/Harmonic 依赖安装提示和新的多机器人 `robot_mappings` 示例。
- 注意事项：
  - 多机器人桥接参数应改为 `/model/robot_001/odometry` 这类 Gazebo Sim 话题。
  - 仍需在用户 WSL 2 环境中执行真实 Gazebo Sim 联调。

### 2026-06-10 - 修复 ROS 2 topic 中划线非法问题

- 用户反馈：
  - 巡航节点仍在生成 `/model/robot-001/cmd_vel`，ROS 2 对 topic/name 中的中划线不友好，导致运行时报错。
- 完成内容：
  - 保持 RMS 业务主键仍为 `robot-001`，用于 PostgreSQL、Kafka payload 和前端展示。
  - 新增 ROS/Gazebo 安全命名规则：`robot-001` 转为 `robot_001`。
  - `rms_warehouse_5robots.launch.py` 中 Gazebo 模型名改为 `robot_001` 到 `robot_005`。
  - `gz_topic()` 和 `cmd_vel_topic()` 统一输出 `/model/robot_001/...` 这类 ROS 2 安全话题。
  - 更新 bridge/patrol 测试、README 多机器人 mapping 示例。
- 注意事项：
  - 多机器人桥接参数格式应是 `robot-001:/model/robot_001/odometry`：冒号左侧是 RMS robotId，右侧是 ROS 2 安全 topic。

### 2026-06-10 - 修复 rclpy.Node 保留属性命名冲突

- 用户反馈：
  - 启动 `rms_bridge_node` 时报错：`AttributeError: property 'subscriptions' of 'RmsBridgeNode' object has no setter`。
  - 根因是 `rclpy.Node` 已经内置只读属性 `subscriptions`，桥接节点自定义字段 `self.subscriptions` 与其冲突。
- 完成内容：
  - `RmsBridgeNode` 中的订阅保存字段从 `self.subscriptions` 改为 `self._odom_subscriptions`。
  - `RmsPatrolNode` 中的发布器保存字段从 `self.publishers` 改为 `self._cmd_vel_publishers`，避免未来和 ROS 2 Node 底层属性名冲突。
- 验证情况：
  - 已运行 Python compileall，`rms_bridge_node` 和 `rms_warehouse_sim` 语法编译通过。
  - 已运行轻量回归，确认 Gazebo Sim 话题和多机器人 mapping 解析正常。

### 2026-06-10 - 修复 ROS 2 巡航节点可执行入口易用性

- 用户反馈：
  - 在 WSL 2 中执行 `ros2 run rms_warehouse_sim patrol_node.py` 后提示 `No executable found`。
  - 根因是 `setup.py` 只注册了 `rms_patrol_node` 入口，未注册用户按文件名直觉运行的 `patrol_node.py`。
- 完成内容：
  - `ros2/rms_warehouse_sim/setup.py` 新增 console script 别名：`patrol_node.py = rms_warehouse_sim.patrol_node:main`。
  - README 增加推荐命令 `ros2 run rms_warehouse_sim rms_patrol_node`、兼容别名 `patrol_node.py`，以及 `ros2 pkg executables rms_warehouse_sim` 排障命令。
- 注意事项：
  - 修改 `setup.py` 后需要重新 `colcon build --packages-select rms_warehouse_sim` 并重新 `source install/setup.bash`。

### 2026-06-09 - 修复后端启动脚本主类查找问题

- 用户需求：
  - 解决 `Unable to find a suitable main class`，明确后端启动脚本不能再从根父 POM 执行 `spring-boot:run`。
- 完成内容：
  - 修改 `scripts/start-api.ps1`，使用项目根目录计算绝对 `.m2\repository` 路径。
  - 脚本先在根目录安装 `rms-common`，再切换到 `rms-backend-api` 模块执行 `spring-boot:run`。
  - 移除从根目录使用 `-pl rms-backend-api` 启动 Spring Boot 的方式，避免 Spring Boot Maven Plugin 跑到父 POM。
  - 更新 `README.md`，说明根 `pom.xml` 只是多模块聚合父项目，不能直接运行 `spring-boot:run`。
- 涉及文件：
  - `scripts/start-api.ps1`
  - `README.md`
  - `AGENT.md`
- 验证情况：
  - 已运行 `D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=D:\AI_Lab\my_project\robot_management_system\.m2\repository" -pl rms-common -am -DskipTests install`，构建成功。
  - 已运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-api.ps1`。
  - 脚本已进入 `rms-backend-api` 模块执行 `spring-boot:run`，Spring Boot 成功加载 `RobotManagementSystemApplication`，不再出现 `Unable to find a suitable main class`。
  - 当前启动最终失败原因变为 `Port 8080 was already in use`，说明主类查找问题已解决，剩余是端口占用问题。
  - 已运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1`，当前 8080 上已有 API 实例可用，验证通过：机器人数量 24，系统状态 `UP`。
