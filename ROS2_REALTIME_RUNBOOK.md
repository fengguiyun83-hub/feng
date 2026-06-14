# ROS2 实时数据源启动手册

本文档用于把系统从旧 mock/seed 数据切换到 ROS2/Gazebo 仿真数据源。前端显示的机器人数量来自真实接入的 ROS2 telemetry：当前五车仿真会显示 5 台，未来 ROS2 发布 50-100 台时，后端会按 `robotId` 自动注册并展示更多机器人。

## 一、Windows PowerShell 步骤

所有 PowerShell 命令都在项目根目录执行：

```powershell
cd D:\AI_Lab\my_project\robot_management_system
```

### 1. 启动 Docker 基础设施

```powershell
docker compose up -d
docker compose ps
```

期望看到：

- `robot-kafka` running/healthy
- `robot-redis` running/healthy
- `robot-postgres` running/healthy
- `robot-flink-jobmanager` running
- `robot-flink-taskmanager` running

### 2. 清理旧 mock/seed 数据

首次切换到 ROS2-only 数据源时执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\reset-ros2-data.ps1 -IncludeAlerts -ResetKafkaTopic
```

说明：

- 清理 Redis `robot:states`
- 清理 Redis `robot:alerts`
- 清理 PostgreSQL `robot_alerts`
- 删除 PostgreSQL 中 `telemetry_source='seed'` 或 `unknown` 的机器人档案
- 重建 Kafka topic `robot-telemetry`

以后如果只想清状态，不想清告警和 Kafka，可以执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\reset-ros2-data.ps1
```

### 3. 提交 ROS-only Flink 任务

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\submit-ros2-flink-job.ps1
```

脚本会构建 Flink jar、复制到 JobManager、取消旧同名 job，并用下面的核心参数重新提交：

```text
--bootstrap.servers kafka:19092
--group.id robot-ros2-only-flink-v2
--redis.host redis
--alert.threshold 45
--redis.state-stream robot:states
--state.emit-interval-ms 200
--telemetry.required-source ros2-gazebo
```

### 4. 启动后端 API

新开一个 PowerShell 窗口，执行：

```powershell
cd D:\AI_Lab\my_project\robot_management_system
powershell -ExecutionPolicy Bypass -File .\scripts\start-api.ps1
```

这个窗口不要关闭。后端地址：

```text
http://localhost:8080
```

### 5. 启动前端

再新开一个 PowerShell 窗口，执行：

```powershell
cd D:\AI_Lab\my_project\robot_management_system
powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1
```

这个窗口也不要关闭。前端地址：

```text
http://localhost:5173
```

如果浏览器提示无法连接，先在 PowerShell 检查：

```powershell
Invoke-WebRequest http://localhost:5173 -UseBasicParsing
```

如果请求失败，说明前端 Vite 窗口没有运行或已关闭，重新执行 `scripts\start-frontend.ps1`。

## 二、Ubuntu / WSL 终端步骤

所有 Ubuntu 命令都在 WSL 终端执行。

### 1. 构建 ROS2 packages

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source /opt/ros/jazzy/setup.bash
colcon build --packages-select rms_bridge_node rms_warehouse_sim
source install/setup.bash
```

### 2. 启动五车仓库仿真

新开一个 Ubuntu 终端：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source install/setup.bash
ros2 launch rms_warehouse_sim rms_warehouse_5robots.launch.py
```

### 3. 启动巡航节点

再新开一个 Ubuntu 终端：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source install/setup.bash
ros2 run rms_warehouse_sim rms_patrol_node
```

### 4. 启动 ROS2 -> Kafka bridge

再新开一个 Ubuntu 终端：

```bash
cd /mnt/d/AI_Lab/my_project/robot_management_system/ros2
source install/setup.bash
ros2 run rms_bridge_node rms_bridge_node --ros-args \
  -p kafka_bootstrap_servers:=localhost:9092 \
  -p kafka_topic:=robot-telemetry \
  -p robot_mappings:=robot-001:/model/robot_001/odometry,robot-002:/model/robot_002/odometry,robot-003:/model/robot_003/odometry,robot-004:/model/robot_004/odometry,robot-005:/model/robot_005/odometry
```

不要使用 `10.25.x.x:29092`。当前 Kafka 给宿主机/WSL 客户端使用的地址是：

```text
localhost:9092
```

## 三、验收命令

### 1. 检查 ROS2 odometry

Ubuntu / WSL：

```bash
ros2 topic list | grep '/model/robot_00[1-5]/odometry'
ros2 topic echo --once /model/robot_001/odometry --field pose.pose.position
```

### 2. 检查 Redis 状态流

Windows PowerShell：

```powershell
docker exec robot-redis redis-cli XLEN robot:states
docker exec robot-redis redis-cli XREVRANGE robot:states + - COUNT 20
```

最近记录应包含：

```text
robotId
robot-001
source
ros2-gazebo
```

并且能看到 `robot-001` 到 `robot-005`。

### 3. 检查 PostgreSQL 自动注册

Windows PowerShell：

```powershell
docker exec robot-postgres psql -U robot -d robot_management -c "SELECT robot_id, telemetry_source, position_x, position_y, last_seen_at FROM robots ORDER BY robot_id;"
```

当前五车仿真下，应只看到真实接入过的 ROS2 机器人，`telemetry_source` 为 `ros2-gazebo`。

### 4. 检查后端 API

Windows PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1 -ExpectedRobotCount 5
```

如果未来 ROS2 扩展到 50 台，可以执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1 -ExpectedRobotCount 50
```

或者跳过固定数量检查：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-api.ps1 -ExpectedRobotCount -1
```

### 5. 打开前端

浏览器访问：

```text
http://localhost:5173
```

前端应显示当前 ROS2 已接入机器人，不再显示旧 24 条 seed/mock 数据。

## 四、常见问题

### 前端无法连接 `localhost:5173`

原因通常是 `scripts\start-frontend.ps1` 所在 PowerShell 窗口被关闭。重新打开 PowerShell 并执行：

```powershell
cd D:\AI_Lab\my_project\robot_management_system
powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1
```

### bridge 报 `NoBrokersAvailable`

确认 bridge 使用：

```text
localhost:9092
```

不要用：

```text
10.25.x.x:29092
```

并确认 Windows PowerShell 中：

```powershell
docker compose ps
```

显示 `robot-kafka` 正在运行。

### 前端机器人数量不对

先清理旧数据：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\reset-ros2-data.ps1 -IncludeAlerts -ResetKafkaTopic
```

然后重新提交 Flink、启动 ROS2 bridge，并访问：

```powershell
Invoke-RestMethod http://localhost:8080/api/robots
```

返回数量应等于当前 ROS2 实际发送 telemetry 的机器人数量。
