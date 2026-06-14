import ReactECharts from "echarts-for-react";
import { Descriptions, Drawer, Empty, Progress, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { formatDateTime, formatTime, statusColors, statusLabels } from "../constants";
import type { RobotAlert, RobotSnapshot, RobotStatus } from "../types";

const { Text } = Typography;

interface RobotDetailDrawerProps {
  robot: RobotSnapshot | null;
  alerts: RobotAlert[];
  open: boolean;
  onClose: () => void;
}

export function RobotDetailDrawer({ robot, alerts, open, onClose }: RobotDetailDrawerProps) {
  const robotAlerts = robot ? alerts.filter((alert) => alert.robotId === robot.robotId) : [];
  const trendOption = robot
    ? {
        grid: { left: 34, right: 18, top: 22, bottom: 24 },
        tooltip: { trigger: "axis" },
        xAxis: {
          type: "category",
          data: ["-25m", "-20m", "-15m", "-10m", "-5m", "now"]
        },
        yAxis: { type: "value", min: 35, name: "°C" },
        series: [
          {
            type: "line",
            smooth: true,
            data: Array.from({ length: 6 }, (_, index) =>
              Number((robot.temperatureCelsius - 1.2 + index * 0.24 + (index % 2) * 0.18).toFixed(2))
            ),
            lineStyle: { color: "#177ddc", width: 3 },
            itemStyle: { color: "#177ddc" },
            areaStyle: { color: "rgba(23, 125, 220, 0.12)" }
          }
        ]
      }
    : {};

  const alertColumns: ColumnsType<RobotAlert> = [
    { title: "时间", dataIndex: "alertTime", key: "alertTime", render: formatTime },
    {
      title: "平均温度",
      dataIndex: "avgTemperature",
      key: "avgTemperature",
      render: (value: number) => <Tag color={value >= 45 ? "volcano" : "orange"}>{value.toFixed(2)} °C</Tag>
    },
    { title: "事件数", dataIndex: "eventCount", key: "eventCount" }
  ];

  return (
    <Drawer width={560} title={robot ? `${robot.name} 详情` : "机器人详情"} open={open} onClose={onClose}>
      {robot ? (
        <Space direction="vertical" size={18} className="drawer-content">
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="机器人编号">{robot.robotId}</Descriptions.Item>
            <Descriptions.Item label="型号">{robot.model}</Descriptions.Item>
            <Descriptions.Item label="位置">{robot.location}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={statusColors[robot.status as RobotStatus]}>{statusLabels[robot.status as RobotStatus]}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="当前温度">{robot.temperatureCelsius.toFixed(2)} °C</Descriptions.Item>
            <Descriptions.Item label="电量">
              <Progress percent={Math.round(robot.batteryPercent)} size="small" />
            </Descriptions.Item>
            <Descriptions.Item label="最后上报">{formatDateTime(robot.lastSeenAt)}</Descriptions.Item>
          </Descriptions>

          <section>
            <Text strong>短时温度趋势</Text>
            <ReactECharts option={trendOption} className="detail-chart" />
          </section>

          <section>
            <Text strong>关联告警</Text>
            <Table
              rowKey="id"
              size="small"
              columns={alertColumns}
              dataSource={robotAlerts}
              pagination={{ pageSize: 5, showSizeChanger: false }}
              locale={{ emptyText: <Empty description="暂无关联告警" /> }}
            />
          </section>
        </Space>
      ) : (
        <Empty description="请选择机器人" />
      )}
    </Drawer>
  );
}
