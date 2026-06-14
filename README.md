# Robot Management System

这是一个机器人遥测实时处理与告警原型项目。当前项目已经具备 Kafka、Flink、Redis 组成的实时数据链路：ROS 2/Gazebo 仿真机器人持续上报遥测数据，Flink 计算窗口平均温度并生成告警，同时把限流后的实时位姿写入 Redis Stream，后端再同步到 PostgreSQL 和前端数字孪生驾驶舱。

它目前还不是完整的机器人管理系统，但已经从单体原型演进为 Maven 多模块项目。后续会继续按小步迭代方式补齐命令交互、权限审计、遥测历史分析等能力。

## 当前能力

- 使用 Maven 多模块拆分公共模型、Flink 作业和 Spring Boot 后端 API。
- 使用 Docker Compose 启动 Kafka、Redis、Flink JobManager、Flink TaskManager 和 PostgreSQL。
- 自动创建 Kafka topic：`robot-telemetry`。
- ROS-only 实时链路默认只接受 `source=ros2-gazebo` 的遥测数据，避免旧 mock 数据覆盖真实位姿。
- 使用 Flink 从 Kafka 消费遥测数据，按机器人计算 5 秒滑动窗口平均温度。
- 当平均温度超过阈值时，将告警写入 Redis Stream：`robot:alerts`。
- 从 ROS 2 Jazzy / Gazebo Sim Harmonic 接入仿真位姿，经 Kafka/Flink 限流后写入 Redis Stream：`robot:states`。
- 使用 PostgreSQL 保存机器人档案和告警历史，并同步保存 Redis 最近告警。
- 提供 Spring Boot REST API，用于机器人列表、机器人详情、告警列表、告警确认关闭、总览摘要和系统状态查询。
- 提供 SSE 实时推送接口 `/api/stream/dashboard`，用于前端驾驶舱自动刷新。
- 提供 React + TypeScript 前端驾驶舱，包含总览、2D 数字孪生、机器人、告警、系统视图、机器人详情抽屉和告警处理操作。

## 技术栈

- Java 17
- Spring Boot 3.3.2
- Maven
- Kafka
- Apache Flink 1.19.1
- Redis Stream
- PostgreSQL
- Spring Data JPA
- Flyway
- Docker Compose
- React + TypeScript + Vite
- Ant Design
- ECharts
- ROS 2 Jazzy + Gazebo Sim Harmonic + ros_gz

## 项目结构

```text
.
├── docker-compose.yml
├── pom.xml
├── rms-common
│   └── src/main/java/com/example/robotmanagementsystem
│       ├── model/
│       └── persistence/
├── rms-flink-job
│   └── src/main/java/com/example/robotmanagementsystem/flink/
├── rms-backend-api
│   └── src
│       ├── main
│       │   ├── java/com/example/robotmanagementsystem
│       │   │   ├── RobotManagementSystemApplication.java
│       │   │   ├── api/
│       │   │   └── service/
│       │   └── resources/db/migration/
│       └── test/java/com/example/robotmanagementsystem/
├── frontend
│   └── src
│       ├── components/
│       ├── hooks/
│       └── views/
├── ros2
│   ├── rms_bridge_node/
│   └── rms_warehouse_sim/
├── scripts
└── AGENT.md
```

## 环境准备

需要本机具备：

- Docker Desktop
- Java 17 或更高版本
- Maven

当前环境中推荐使用本机 Maven：

```powershell
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository"
```

项目也包含 Maven Wrapper：

```powershell
.\mvnw.cmd
```

但当前机器上 `mvnw.cmd` 可能遇到用户目录 `.m2\wrapper` 权限问题。如果 wrapper 失败，优先使用上面的本机 Maven 路径。

## 启动基础设施

```powershell
docker compose up -d
docker compose ps
```

期望状态：

- `robot-kafka`：running/healthy
- `robot-redis`：running/healthy
- `robot-postgres`：running/healthy
- `robot-flink-jobmanager`：running
- `robot-flink-taskmanager`：running

Kafka 服务监听本机端口 `9092`，Redis 监听本机端口 `6379`，Flink Web UI 监听 `8081`。
PostgreSQL 监听本机端口 `5432`，默认数据库为 `robot_management`，用户和密码均为 `robot`。

## 构建项目

```powershell
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" clean package -DskipTests
```

构建成功后应生成：

```text
rms-common\target\rms-common-0.0.1-SNAPSHOT.jar
rms-flink-job\target\rms-flink-job-0.0.1-SNAPSHOT.jar
rms-backend-api\target\rms-backend-api-0.0.1-SNAPSHOT.jar
```

## 提交 Flink 任务

先把 jar 放入 Flink JobManager 容器：

```powershell
docker exec robot-flink-jobmanager mkdir -p /opt/flink/usrlib
docker cp rms-flink-job\target\rms-flink-job-0.0.1-SNAPSHOT.jar robot-flink-jobmanager:/opt/flink/usrlib/robot-job.jar
```

提交 ROS-only 任务：

```powershell
docker exec robot-flink-jobmanager flink run -d /opt/flink/usrlib/robot-job.jar --bootstrap.servers kafka:19092 --group.id robot-ros2-only-flink-v1 --redis.host redis --alert.threshold 40 --redis.state-stream robot:states --state.emit-interval-ms 200 --telemetry.required-source ros2-gazebo
```

查看任务状态：

```powershell
docker exec robot-flink-jobmanager flink list
```

也可以打开 Flink Web UI：

```text
http://localhost:8081
```

说明：`--group.id robot-ros2-only-flink-v1` 用于避开旧 consumer group 中可能积压的 mock 数据；`--telemetry.required-source ros2-gazebo` 会让 Flink 只处理 ROS 2/Gazebo 真数据；`--state.emit-interval-ms 200` 会把 ROS 2 `/odom` 高频状态流按机器人限流到约 5Hz，避免冲垮 Redis 和后端同步。

## 旧遥测生产测试

`RobotTelemetryKafkaProducerTest` 是早期遗留的 Kafka 压测/演示入口，会向 `robot-telemetry` 发送 72,000 条 `source=unknown` 的旧格式 mock 数据。第十轮后它已经被 `@Disabled` 标记为 deprecated/manual-only，不再作为正常测试或验收步骤使用。

当前推荐只使用 ROS 2 bridge 写入 Kafka：

```bash
ros2 run rms_bridge_node rms_bridge_node --ros-args \
  -p kafka_bootstrap_servers:=localhost:9092 \
  -p kafka_topic:=robot-telemetry \
  -p robot_mappings:=robot-001:/model/robot_001/odometry,robot-002:/model/robot_002/odometry,robot-003:/model/robot_003/odometry,robot-004:/model/robot_004/odometry,robot-005:/model/robot_005/odometry
```

如果误运行过旧模拟器，先清理 Redis 状态流并重新提交 ROS-only Flink job：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\clear-ros-state.ps1
```

## 查看 Redis 告警结果

查看告警数量：

```powershell
docker exec robot-redis redis-cli XLEN robot:alerts
```

查看最近 5 条告警：

```powershell
docker exec robot-redis redis-cli XREVRANGE robot:alerts + - COUNT 5
```

验收标准：

- `XLEN robot:alerts` 返回大于 `0` 的数字。
- 最近告警记录包含：
  - `robotId`
  - `avgTemperature`
  - `windowStart`
  - `windowEnd`
  - `eventCount`
  - `alertTime`
  - `payload`

## 停止服务

```powershell
docker compose down
```

如果需要同时删除 Redis 数据卷：

```powershell
docker compose down -v
```

执行 `down -v` 会删除本地 Redis 持久化数据，请确认不需要保留历史告警后再使用。

## 当前未实现功能

后续会逐步补齐：

- 真实遥测历史查询和聚合 API。
- 命令下发和命令执行记录。
- 用户权限、审计日志和系统监控。

## 后续迭代计划

1. 告警处理增强
   - 增加告警处理人选择、批量处理、处理记录时间线。
2. 机器人管理增强
   - 增加机器人注册、编辑、维护记录和命令下发。
3. 遥测历史分析
   - 保存遥测摘要或时序数据，提供趋势和统计接口。
4. 权限与审计
   - 增加登录、角色权限、操作审计和系统监控。

## 前端驾驶舱雏形

第四轮迭代后，项目新增了 `frontend/` 前端应用：

- React + TypeScript + Vite
- Ant Design
- ECharts
- Lucide React 图标

安装依赖：

```powershell
cd frontend
npm.cmd install --no-audit --no-fund
```

如果 npm 安装中途超时但 `node_modules` 已生成，可先补锁文件：

```powershell
npm.cmd install --package-lock-only --no-audit --no-fund
```

启动前端开发服务器：

```powershell
npm.cmd run dev
```

默认地址：

```text
http://localhost:5173
```

构建前端：

```powershell
npm.cmd run build
```

页面能力：

- 总览：机器人数量、在线数、未处理告警数、Redis 告警数、平均温度、温度分布图、最近告警。
- 孪生：基于 Redis `robot:states` 和 SSE 的 2D 实时位姿视图。
- 机器人：机器人列表、状态筛选、关键词搜索。
- 告警：告警状态筛选、确认告警、关闭告警和运维备注。
- 系统：后端、Redis、Kafka、Flink 基础状态。

前端通过 Vite proxy 请求后端 API。使用前请先启动后端：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-api.ps1
```

## 项目记忆

项目演进记录保存在：

```text
AGENT.md
```

后续每次重要改动都应追加记录，方便持续迭代。

## 后端 API 雏形

第二轮迭代后，Spring Boot 应用已经提供第一版 REST API。第六轮后，机器人档案和告警历史已经接入 PostgreSQL。

第七轮迭代后，项目已经拆分为 Maven 多模块：

- `rms-common`：共享 DTO、Entity、枚举和 Repository。
- `rms-flink-job`：纯 Flink/Kafka/Redis 流处理模块，用 Maven Shade 输出干净的 Flink job jar。
- `rms-backend-api`：Spring Boot 后端 API、Service、Flyway migration 和测试。

第六轮迭代后，后端增加 PostgreSQL 持久化：

- 机器人档案保存到 `robots` 表。
- Redis 告警同步保存到 `robot_alerts` 表。
- Spring Boot 默认不再写入 `robot-001` 到 `robot-024` 这类演示 seed 数据；机器人档案由 ROS 2/Gazebo telemetry 自动注册。详见 `ROS2_REALTIME_RUNBOOK.md`。
- `/api/alerts`、`/api/robots/{robotId}/alerts`、dashboard summary 和 SSE snapshot 会在读取告警前轻量同步 Redis 最近告警到 PostgreSQL。
- Redis 不可用时，告警接口返回数据库中已有的历史告警，不让 API 崩溃。
- Kafka/Flink 仍只写 Redis Stream，不直接写 PostgreSQL。

第七轮迭代后，告警具备生命周期状态：

- `NEW`：新告警，计入未处理告警数。
- `ACKNOWLEDGED`：已确认，不再计入未处理告警数。
- `CLOSED`：已关闭，不再计入未处理告警数。

Redis 重复同步同一个告警 ID 时不会覆盖已处理状态和运维备注。

## 推荐开发工作流

第三轮迭代后，项目提供了 `scripts/` 下的 PowerShell 脚本，用来稳定启动和验收本地环境。

如果当前 PowerShell 禁止直接运行 `.ps1`，可以使用下面这种方式执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1
```

1. 启动 Docker 基础设施：

   ```powershell
   docker compose up -d
   ```

   这会启动 Kafka、Redis、Flink 和 PostgreSQL。第一次启动 PostgreSQL 后，需要启动一次后端 API，让 Flyway 创建表并写入初始机器人档案。

2. 在一个终端启动后端 API：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\start-api.ps1
   ```

3. 在另一个终端验证 API：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1
   ```

4. 验证 Kafka/Flink/Redis 流处理环境：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\verify-stream.ps1
   ```

   这个脚本用于验收 Kafka/Flink/Redis 告警链路。Redis Stream `robot:alerts` 查询是核心检查项；`docker compose ps` 和 Flink job 状态只作为辅助信息展示。PostgreSQL 和后端 API 请使用 `verify-api.ps1` 或后端测试单独验证。

   如果脚本显示 `robot:alerts` 数量为 `0`，表示当前还没有生成 ROS 2 告警数据，不代表脚本执行失败。请确认 Flink job 使用了 `--telemetry.required-source ros2-gazebo`，并保持 ROS 2 bridge 正在向 Kafka 写入数据。

5. 运行 API 自动化测试：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1
   ```

说明：`start-api.ps1` 默认前台运行 Spring Boot，不自动创建后台进程，也不自动停止 Java 进程。脚本会先把 `rms-common` 安装到项目内 `.m2\repository`，再切换到 `rms-backend-api` 模块执行 `spring-boot:run`。需要停止 API 时，在启动 API 的终端按 `Ctrl+C`。

根目录 `pom.xml` 只是 Maven 多模块聚合父项目，不能直接在根目录运行 `spring-boot:run`。如果需要手动启动后端，请先安装公共模块，再进入 `rms-backend-api` 目录：

```powershell
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=D:\AI_Lab\my_project\robot_management_system\.m2\repository" -pl rms-common -am -DskipTests install
cd rms-backend-api
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=D:\AI_Lab\my_project\robot_management_system\.m2\repository" spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

可用接口：

```text
GET /api/robots
GET /api/robots?status=ONLINE
GET /api/robots?keyword=robot-001
GET /api/robots/{robotId}
GET /api/robots/{robotId}/alerts
GET /api/alerts
GET /api/alerts?limit=20
GET /api/alerts?status=NEW
POST /api/alerts/{alertId}/acknowledge
POST /api/alerts/{alertId}/close
GET /api/dashboard/summary
GET /api/system/status
GET /api/stream/dashboard
```

示例：

```powershell
Invoke-RestMethod http://localhost:8080/api/robots
Invoke-RestMethod http://localhost:8080/api/robots/robot-001
Invoke-RestMethod http://localhost:8080/api/dashboard/summary
Invoke-RestMethod http://localhost:8080/api/system/status
```

确认和关闭告警示例：

```powershell
Invoke-RestMethod -Method Post -ContentType "application/json" -Body '{"operator":"运维人员","note":"已接手处理"}' http://localhost:8080/api/alerts/{alertId}/acknowledge
Invoke-RestMethod -Method Post -ContentType "application/json" -Body '{"operator":"运维人员","note":"温度恢复正常，现场检查完成"}' http://localhost:8080/api/alerts/{alertId}/close
```

说明：确认备注可选，关闭备注必填。已关闭告警再次确认或关闭会返回 `409 Conflict`。

API 测试：

```powershell
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" -pl rms-backend-api -am -Dtest=RobotApiControllerTest,DashboardStreamControllerTest,RobotPersistenceTest,AlertPersistenceServiceTest,RobotStateSyncServiceTest test
```

PostgreSQL 验收查询：

```powershell
docker exec robot-postgres psql -U robot -d robot_management -c "SELECT COUNT(*) FROM robots;"
docker exec robot-postgres psql -U robot -d robot_management -c "SELECT COUNT(*) FROM robot_alerts;"
docker exec robot-postgres psql -U robot -d robot_management -c "SELECT status, COUNT(*) FROM robot_alerts GROUP BY status;"
docker exec robot-postgres psql -U robot -d robot_management -c "SELECT robot_id, position_x, position_y, yaw_radians, telemetry_source FROM robots WHERE robot_id = 'robot-001';"
```

说明：`robot_alerts` 需要调用 `/api/alerts` 或 dashboard/SSE 相关接口后才会从 Redis 同步写入。

## 第五轮：驾驶舱体验优化和 SSE

第五轮迭代后，后端新增 SSE 实时推送接口：

```text
GET /api/stream/dashboard
```

该接口返回 `text/event-stream`，每 5 秒推送一次名为 `dashboard` 的事件。事件 payload 包含：

- `summary`：总览摘要。
- `alerts`：最近告警。
- `system`：系统状态。
- `timestamp`：快照生成时间。

后端连接管理策略：

- 使用 `CopyOnWriteArrayList<SseEmitter>` 管理 SSE 连接。
- 每个连接注册 `onCompletion`、`onTimeout`、`onError` 清理回调。
- 使用单线程 `ScheduledExecutorService` 定时广播，避免多个线程同时写同一个 emitter。
- 广播失败时捕获 `IOException`、`IllegalStateException`，立即移除坏连接。
- emitter 超时时间为 30 分钟。

前端体验优化：

- `useDashboardData` 统一管理 REST 初始化、手动刷新、SSE 更新和连接状态。
- SSE 正常连接时自动刷新总览、告警和系统状态。
- 连接异常 30 秒内显示橙色“连接尝试中...”，超过 30 秒显示红色“实时连接已断开”。
- 页面提供手动重连按钮。
- 组件卸载时关闭旧 `EventSource`，避免前端连接泄漏。
- 新增机器人详情抽屉，点击机器人列表行可查看基础信息、状态、电量、温度、最后上报时间、关联告警和短时趋势。

第五轮验证命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1
cd frontend
npm.cmd run build
```

说明：`npm.cmd run build` 当前会提示 chunk 大于 500 kB，这是 Ant Design 和 ECharts 在第一版驾驶舱中集中打包导致的构建提醒，不影响运行。后续可以通过按需拆包和懒加载优化。

## 第七轮：多模块和告警生命周期

第七轮迭代后，根 `pom.xml` 只作为 aggregator/parent，实际代码分别位于 `rms-common`、`rms-flink-job`、`rms-backend-api`。这样 Spring Boot/JPA/PostgreSQL 依赖不会混入 Flink job 胖包，后续维护会清爽很多。

告警处理闭环已经落地到后端和前端：

- 后端通过 Flyway V2 为 `robot_alerts` 增加状态、确认人、关闭人、运维备注和更新时间字段。
- `/api/alerts` 支持 `status` 筛选。
- `/api/alerts/{alertId}/acknowledge` 支持 `NEW -> ACKNOWLEDGED`。
- `/api/alerts/{alertId}/close` 支持 `NEW/ACKNOWLEDGED -> CLOSED`。
- `DashboardSummary.openAlertCount` 只统计 `NEW` 告警。
- 前端告警中心支持状态筛选、确认、关闭和备注填写。

第七轮验证命令：

```powershell
D:\Tools\apache-maven-3.9.9\bin\mvn.cmd "-Dmaven.repo.local=.m2/repository" clean package -DskipTests
powershell -ExecutionPolicy Bypass -File .\scripts\run-api-tests.ps1
cd frontend
npm.cmd run build
```

## 第八轮：ROS 2 仿真和 2D 数字孪生

第八轮引入 WSL 2 内的 ROS 2 仿真桥接。当前推荐环境为 Ubuntu 24.04 + ROS 2 Jazzy + Gazebo Sim Harmonic。桥接节点 `ros2/rms_bridge_node` 订阅里程计话题，将位姿和速度转换为 RMS Telemetry v2 JSON，并写入 Windows Docker Kafka `robot-telemetry`。

Telemetry v2 关键字段：

```json
{
  "schemaVersion": "rms.telemetry.v2",
  "source": "ros2-gazebo",
  "robotId": "robot-001",
  "timestamp": 1780963200000,
  "rosStampMillis": 1780963200000,
  "bridgeSentAtMillis": 1780963200123,
  "temperatureCelsius": 42.5,
  "batteryPercent": 86.0,
  "motorCurrentAmp": 3.2,
  "positionX": 1.25,
  "positionY": -0.4,
  "positionZ": 0.0,
  "yawRadians": 1.57,
  "linearVelocity": 0.22,
  "angularVelocity": 0.05
}
```

Flink 会继续生成 `robot:alerts` 温度告警，同时把状态写入 `robot:states`。由于 `/odom` 常见频率为 30Hz 以上，Flink 已按 `robotId` 做状态流降频，默认 `--state.emit-interval-ms 200`，约等于每台机器人 5Hz。第十轮后默认使用 `--telemetry.required-source ros2-gazebo`，旧 `source=unknown` mock 数据不会进入状态流或告警流。

WSL 2 中准备桥接节点：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
python3 -m pip install --user kafka-python pytest
colcon build --packages-select rms_bridge_node
source install/setup.bash
```

如果你仍在旧的 Humble + Gazebo Classic 环境中，可以继续用 TurtleBot3 示例；Jazzy 环境请优先使用下一节的 Gazebo Sim 智能仓库 world。

```bash
export TURTLEBOT3_MODEL=burger
ros2 launch turtlebot3_gazebo turtlebot3_world.launch.py
```

另开一个 WSL 终端启动桥接节点：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source install/setup.bash
ros2 run rms_bridge_node rms_bridge_node --ros-args \
  -p robot_id:=robot-001 \
  -p odom_topic:=/odom \
  -p kafka_bootstrap_servers:=localhost:9092 \
  -p kafka_topic:=robot-telemetry
```

Windows 侧提交 Flink job：

```powershell
docker exec robot-flink-jobmanager flink run -d /opt/flink/usrlib/robot-job.jar --bootstrap.servers kafka:19092 --redis.host redis --alert.threshold 40 --redis.state-stream robot:states --state.emit-interval-ms 200
```

第十轮后推荐使用 ROS-only 参数和新的 consumer group：

```powershell
docker exec robot-flink-jobmanager flink run -d /opt/flink/usrlib/robot-job.jar --bootstrap.servers kafka:19092 --group.id robot-ros2-only-flink-v1 --redis.host redis --alert.threshold 40 --redis.state-stream robot:states --state.emit-interval-ms 200 --telemetry.required-source ros2-gazebo
```

验收命令：

```powershell
docker exec robot-redis redis-cli XLEN robot:states
docker exec robot-redis redis-cli XREVRANGE robot:states + - COUNT 1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-stream.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1
```

如果数字孪生页仍显示 `0.00 / unknown`，先执行 ROS-only 清理：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\clear-ros-state.ps1
```

然后停止旧 Flink job，使用上面的 `robot-ros2-only-flink-v1` 命令重新提交，并重新运行 ROS 2 bridge。

时钟防抖策略：

- Python 桥接节点同时发送 `rosStampMillis` 和 `bridgeSentAtMillis`。
- Flink 使用 `timestamp` 做事件时间；如果时间戳无法解析、早于当前时间 10 分钟以上，或晚于当前时间 2 分钟以上，则回退到 Flink 处理时间。
- 原始时间字段会继续写入 `robot:states`，方便排查 WSL 2 clock drift。

前端新增“孪生”页，使用 ECharts 2D 坐标画布展示机器人位置、朝向、速度、电量、电机电流、温度和 telemetry source。Three.js 3D 模型留到后续迭代。

## 第九轮：5 机器人智能仓库仿真

第九轮新增 `ros2/rms_warehouse_sim`，提供 Gazebo Sim 智能仓库 world、5 台自包含 AGV 机器人生成 launch 和自动巡航节点。该版本适配 ROS 2 Jazzy / Gazebo Sim Harmonic，不再依赖 `gazebo_ros`、Gazebo Classic 或 `turtlebot3_gazebo`。5 台机器人固定映射到现有 RMS 档案：

```text
robot-001 -> /model/robot_001/odometry, /model/robot_001/cmd_vel
robot-002 -> /model/robot_002/odometry, /model/robot_002/cmd_vel
robot-003 -> /model/robot_003/odometry, /model/robot_003/cmd_vel
robot-004 -> /model/robot_004/odometry, /model/robot_004/cmd_vel
robot-005 -> /model/robot_005/odometry, /model/robot_005/cmd_vel
```

注意：`robot-001` 是 RMS/PostgreSQL/Kafka 里的业务主键；ROS 2 和 Gazebo Sim 的模型名、话题名使用 `robot_001`，避免中划线违反 ROS 2 topic/name 规则。

Jazzy / Harmonic 依赖提示：

```bash
sudo apt update
sudo apt install ros-jazzy-ros-gz
```

如果你的发行版把包拆得更细，再补装 `ros-jazzy-ros-gz-sim` 和 `ros-jazzy-ros-gz-bridge`。

WSL 2 中构建 ROS 2 packages：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
python3 -m pip install --user kafka-python pytest
colcon build --packages-select rms_bridge_node rms_warehouse_sim
source install/setup.bash
```

启动 5 机器人智能仓库 world：

```bash
ros2 launch rms_warehouse_sim rms_warehouse_5robots.launch.py
```

另开 WSL 终端启动多机器人桥接：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source install/setup.bash
ros2 run rms_bridge_node rms_bridge_node --ros-args \
  -p kafka_bootstrap_servers:=localhost:9092 \
  -p kafka_topic:=robot-telemetry \
  -p robot_mappings:=robot-001:/model/robot_001/odometry,robot-002:/model/robot_002/odometry,robot-003:/model/robot_003/odometry,robot-004:/model/robot_004/odometry,robot-005:/model/robot_005/odometry
```

再开 WSL 终端启动自动巡航：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source install/setup.bash
ros2 run rms_warehouse_sim rms_patrol_node
```

兼容别名也可用：

```bash
ros2 run rms_warehouse_sim patrol_node.py
```

如果提示 `No executable found`，通常是刚改过 `setup.py` 但还没有重新构建或没有重新 source：

```bash
colcon build --packages-select rms_warehouse_sim
source install/setup.bash
ros2 pkg executables rms_warehouse_sim
```

端到端验收：

```powershell
docker exec robot-redis redis-cli XREVRANGE robot:states + - COUNT 20
powershell -ExecutionPolicy Bypass -File .\scripts\verify-stream.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1
```

预期结果：

- `robot:states` 最近记录中能看到 `robot-001` 到 `robot-005`。
- `/api/robots` 中 5 台机器人的 `telemetrySource` 变成 `ros2-gazebo`。
- 前端“孪生”页显示 5 台机器人在智能仓库坐标中移动。
