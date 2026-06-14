import ReactECharts from "echarts-for-react";
import { Empty, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { formatDateTime, statusColors, statusLabels } from "../constants";
import type { RobotSnapshot } from "../types";

const { Text } = Typography;

interface DigitalTwinViewProps {
  robots: RobotSnapshot[];
}

const robotColors = ["#177ddc", "#19a974", "#d46b08", "#722ed1", "#d4380d"];

function axisRange(values: number[], fallbackMin: number, fallbackMax: number) {
  if (!values.length) {
    return { min: fallbackMin, max: fallbackMax };
  }
  const minValue = Math.min(...values, fallbackMin);
  const maxValue = Math.max(...values, fallbackMax);
  const span = Math.max(maxValue - minValue, 1);
  const padding = Math.max(span * 0.15, 1);
  return {
    min: Math.floor(minValue - padding),
    max: Math.ceil(maxValue + padding)
  };
}

function colorForRobot(robotId: string) {
  const match = robotId.match(/(\d+)$/);
  const index = match ? Number(match[1]) - 1 : 0;
  return robotColors[Math.abs(index) % robotColors.length];
}

export function DigitalTwinView({ robots }: DigitalTwinViewProps) {
  const activeRobots = robots.filter((robot) => robot.telemetrySource && robot.telemetrySource !== "seed");
  const xRange = axisRange(activeRobots.map((robot) => robot.positionX), -7, 7);
  const yRange = axisRange(activeRobots.map((robot) => robot.positionY), -5, 5);

  const twinOption = {
    grid: { left: 48, right: 28, top: 32, bottom: 44 },
    tooltip: {
      trigger: "item",
      formatter: (params: { data: [number, number, string, number, number, string] }) => {
        const [x, y, robotId, velocity, yaw, source] = params.data;
        return `${robotId}<br/>X: ${x.toFixed(2)} m<br/>Y: ${y.toFixed(2)} m<br/>速度: ${velocity.toFixed(2)} m/s<br/>朝向: ${yaw.toFixed(2)} rad<br/>来源: ${source}`;
      }
    },
    xAxis: { type: "value", name: "X / m", min: xRange.min, max: xRange.max, splitLine: { lineStyle: { color: "#e8eef5" } } },
    yAxis: { type: "value", name: "Y / m", min: yRange.min, max: yRange.max, splitLine: { lineStyle: { color: "#e8eef5" } } },
    series: [
      {
        name: "货架区 A",
        type: "custom",
        coordinateSystem: "cartesian2d",
        silent: true,
        renderItem: (_: unknown, api: any) => {
          const start = api.coord([-6, 2.2]);
          const end = api.coord([6, 3.15]);
          return {
            type: "rect",
            shape: { x: start[0], y: end[1], width: end[0] - start[0], height: start[1] - end[1] },
            style: { fill: "rgba(212, 107, 8, 0.12)", stroke: "rgba(212, 107, 8, 0.35)" }
          };
        },
        data: [0]
      },
      {
        name: "货架区 B",
        type: "custom",
        coordinateSystem: "cartesian2d",
        silent: true,
        renderItem: (_: unknown, api: any) => {
          const start = api.coord([-6, -3.15]);
          const end = api.coord([6, -2.2]);
          return {
            type: "rect",
            shape: { x: start[0], y: end[1], width: end[0] - start[0], height: start[1] - end[1] },
            style: { fill: "rgba(23, 125, 220, 0.10)", stroke: "rgba(23, 125, 220, 0.32)" }
          };
        },
        data: [0]
      },
      {
        name: "AGV direction",
        type: "custom",
        coordinateSystem: "cartesian2d",
        silent: true,
        renderItem: (_: unknown, api: any) => {
          const x = api.value(0);
          const y = api.value(1);
          const yaw = api.value(2);
          const color = api.value(4);
          const start = api.coord([x, y]);
          const end = api.coord([x + Math.cos(yaw) * 0.6, y + Math.sin(yaw) * 0.6]);
          return {
            type: "line",
            shape: { x1: start[0], y1: start[1], x2: end[0], y2: end[1] },
            style: { stroke: color, lineWidth: 2 }
          };
        },
        data: activeRobots.map((robot) => [
          robot.positionX,
          robot.positionY,
          robot.yawRadians,
          robot.robotId,
          colorForRobot(robot.robotId)
        ])
      },
      {
        name: "AGV position",
        type: "scatter",
        symbolSize: 22,
        data: activeRobots.map((robot) => [
          robot.positionX,
          robot.positionY,
          robot.robotId,
          robot.linearVelocity,
          robot.yawRadians,
          robot.telemetrySource
        ]),
        itemStyle: {
          color: (params: { data: [number, number, string] }) => colorForRobot(params.data[2])
        },
        label: {
          show: true,
          formatter: (params: { data: [number, number, string] }) => params.data[2],
          position: "top",
          color: "#1f2a37",
          fontWeight: 600,
          fontSize: 12,
          backgroundColor: "rgba(255,255,255,0.88)",
          borderColor: "#dce4ee",
          borderWidth: 1,
          borderRadius: 4,
          padding: [3, 5]
        }
      }
    ]
  };

  const columns: ColumnsType<RobotSnapshot> = [
    {
      title: "机器人",
      key: "robot",
      render: (_, robot) => (
        <div className="robot-cell">
          <Text strong>{robot.name}</Text>
          <Text type="secondary">{robot.robotId}</Text>
        </div>
      )
    },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (_, robot) => <Tag color={statusColors[robot.status]}>{statusLabels[robot.status]}</Tag>
    },
    { title: "X", dataIndex: "positionX", key: "positionX", render: (value: number) => value.toFixed(2) },
    { title: "Y", dataIndex: "positionY", key: "positionY", render: (value: number) => value.toFixed(2) },
    { title: "朝向", dataIndex: "yawRadians", key: "yawRadians", render: (value: number) => value.toFixed(2) },
    { title: "线速度", dataIndex: "linearVelocity", key: "linearVelocity", render: (value: number) => `${value.toFixed(2)} m/s` },
    { title: "电机电流", dataIndex: "motorCurrentAmp", key: "motorCurrentAmp", render: (value: number) => `${value.toFixed(2)} A` },
    {
      title: "来源",
      dataIndex: "telemetrySource",
      key: "telemetrySource",
      render: (value: string) => <Tag color={value === "ros2-gazebo" ? "green" : "default"}>{value || "unknown"}</Tag>
    },
    { title: "最后上报", dataIndex: "lastSeenAt", key: "lastSeenAt", render: formatDateTime }
  ];

  return (
    <div className="twin-grid">
      <section className="panel twin-map-panel">
        <div className="section-title">
          <span>智能仓库 2D 数字孪生</span>
          <Text type="secondary">ROS 2 / Gazebo 多机器人实时位姿</Text>
        </div>
        {activeRobots.length ? <ReactECharts option={twinOption} className="twin-chart" /> : <Empty description="等待 ROS 2 桥接状态流" />}
      </section>

      <section className="panel twin-table-panel">
        <Table
          rowKey="robotId"
          size="small"
          columns={columns}
          dataSource={activeRobots}
          pagination={{ pageSize: 8, showSizeChanger: false }}
        />
      </section>
    </div>
  );
}
