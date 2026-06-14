import ReactECharts from "echarts-for-react";
import { Empty, Tag, Typography } from "antd";
import { Activity, Bell, Bot, CheckCircle2, Thermometer, Wifi } from "lucide-react";
import { formatDateTime } from "../constants";
import { Metric } from "../components/Metric";
import type { DashboardSummary, RobotAlert, RobotSnapshot } from "../types";

const { Text } = Typography;

interface DashboardViewProps {
  alerts: RobotAlert[];
  robots: RobotSnapshot[];
  summary: DashboardSummary | null;
}

export function DashboardView({ alerts, robots, summary }: DashboardViewProps) {
  const openAlerts = alerts.filter((alert) => alert.status === "NEW");
  const liveTwinRobots = robots.filter((robot) => robot.telemetrySource && robot.telemetrySource !== "seed");
  const temperatureOption = {
    grid: { left: 36, right: 20, top: 24, bottom: 28 },
    tooltip: { trigger: "axis" },
    xAxis: {
      type: "category",
      data: robots.map((robot) => robot.robotId),
      axisLabel: { interval: 2 }
    },
    yAxis: { type: "value", name: "°C", min: 36 },
    series: [
      {
        type: "line",
        smooth: true,
        symbolSize: 6,
        data: robots.map((robot) => robot.temperatureCelsius),
        lineStyle: { color: "#177ddc", width: 3 },
        itemStyle: { color: "#177ddc" },
        areaStyle: { color: "rgba(23, 125, 220, 0.12)" }
      }
    ]
  };

  return (
    <div className="dashboard-grid">
      <Metric icon={<Bot />} title="机器人总数" value={summary?.robotTotal ?? 0} suffix="台" />
      <Metric icon={<Wifi />} title="在线机器人" value={summary?.onlineRobots ?? 0} suffix="台" />
      <Metric icon={<Bell />} title="未处理告警" value={summary?.openAlertCount ?? openAlerts.length} suffix="条" />
      <Metric icon={<Thermometer />} title="平均温度" value={summary?.averageTemperature ?? 0} precision={2} suffix="°C" />

      <section className="panel chart-panel">
        <div className="section-title">
          <Activity size={18} />
          <span>机器人温度分布</span>
          <Text type="secondary">孪生在线：{liveTwinRobots.length} 台</Text>
        </div>
        {robots.length ? <ReactECharts option={temperatureOption} className="chart" /> : <Empty />}
      </section>

      <section className="panel alert-panel">
        <div className="section-title">
          <CheckCircle2 size={18} />
          <span>待处理告警</span>
        </div>
        {openAlerts.length ? (
          <div className="alert-list">
            {openAlerts.slice(0, 6).map((alert) => (
              <div className="alert-row" key={alert.id}>
                <div>
                  <Text strong>{alert.robotId}</Text>
                  <Text type="secondary">{formatDateTime(alert.alertTime)}</Text>
                </div>
                <Tag color={alert.avgTemperature >= 45 ? "volcano" : "orange"}>
                  {alert.avgTemperature.toFixed(2)} °C
                </Tag>
              </div>
            ))}
          </div>
        ) : (
          <Empty description="暂无待处理告警" />
        )}
      </section>
    </div>
  );
}
