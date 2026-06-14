import { useMemo, useState } from "react";
import {
  Alert,
  Button,
  ConfigProvider,
  Layout,
  Space,
  Spin,
  Tabs,
  Tooltip,
  Typography
} from "antd";
import { Bell, Bot, Gauge, Map, RefreshCw, Server } from "lucide-react";
import { ConnectionAlert, ConnectionStatus } from "./components/ConnectionStatus";
import { RobotDetailDrawer } from "./components/RobotDetailDrawer";
import { useDashboardData } from "./hooks/useDashboardData";
import { AlertCenterView } from "./views/AlertCenterView";
import { DashboardView } from "./views/DashboardView";
import { DigitalTwinView } from "./views/DigitalTwinView";
import { RobotListView } from "./views/RobotListView";
import { SystemView } from "./views/SystemView";
import type { RobotSnapshot } from "./types";

const { Header, Content } = Layout;
const { Text, Title } = Typography;

export function App() {
  const {
    summary,
    robots,
    alerts,
    system,
    loading,
    error,
    connectionState,
    disconnectedSince,
    refresh,
    reconnectStream,
    acknowledgeAlert,
    closeAlert
  } = useDashboardData();
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [keyword, setKeyword] = useState("");
  const [selectedRobot, setSelectedRobot] = useState<RobotSnapshot | null>(null);

  const filteredRobots = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();
    return robots.filter((robot) => {
      const matchesStatus = statusFilter === "ALL" || robot.status === statusFilter;
      const matchesKeyword =
        !normalizedKeyword ||
        robot.robotId.toLowerCase().includes(normalizedKeyword) ||
        robot.name.toLowerCase().includes(normalizedKeyword) ||
        robot.location.toLowerCase().includes(normalizedKeyword);
      return matchesStatus && matchesKeyword;
    });
  }, [keyword, robots, statusFilter]);

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 8,
          colorPrimary: "#177ddc",
          colorInfo: "#177ddc",
          fontFamily: "Inter, Segoe UI, Microsoft YaHei, sans-serif"
        }
      }}
    >
      <Layout className="app-shell">
        <Header className="app-header">
          <div className="brand">
            <div className="brand-mark">
              <Bot size={22} />
            </div>
            <div>
              <Title level={4}>Robot Management</Title>
              <Text>实时遥测运维驾驶舱</Text>
            </div>
          </div>
          <Space size={12}>
            <ConnectionStatus
              state={connectionState}
              disconnectedSince={disconnectedSince}
              onReconnect={reconnectStream}
            />
            <Tooltip title="刷新数据">
              <Button icon={<RefreshCw size={16} />} onClick={() => void refresh()} loading={loading} />
            </Tooltip>
          </Space>
        </Header>

        <Content className="app-content">
          {error && (
            <Alert
              className="top-alert"
              type="warning"
              showIcon
              message="后端 API 暂不可用"
              description={`请先启动后端服务：powershell -ExecutionPolicy Bypass -File .\\scripts\\start-api.ps1。错误：${error}`}
            />
          )}
          <ConnectionAlert state={connectionState} onReconnect={reconnectStream} />

          <Spin spinning={loading && !summary}>
            <Tabs
              defaultActiveKey="dashboard"
              items={[
                {
                  key: "dashboard",
                  label: (
                    <span className="tab-label">
                      <Gauge size={16} /> 总览
                    </span>
                  ),
                  children: <DashboardView alerts={alerts} robots={robots} summary={summary} />
                },
                {
                  key: "twin",
                  label: (
                    <span className="tab-label">
                      <Map size={16} /> 孪生
                    </span>
                  ),
                  children: <DigitalTwinView robots={robots} />
                },
                {
                  key: "robots",
                  label: (
                    <span className="tab-label">
                      <Bot size={16} /> 机器人
                    </span>
                  ),
                  children: (
                    <RobotListView
                      robots={filteredRobots}
                      statusFilter={statusFilter}
                      keyword={keyword}
                      onStatusFilterChange={setStatusFilter}
                      onKeywordChange={setKeyword}
                      onSelectRobot={setSelectedRobot}
                    />
                  )
                },
                {
                  key: "alerts",
                  label: (
                    <span className="tab-label">
                      <Bell size={16} /> 告警
                    </span>
                  ),
                  children: (
                    <AlertCenterView
                      alerts={alerts}
                      onAcknowledge={acknowledgeAlert}
                      onCloseAlert={closeAlert}
                    />
                  )
                },
                {
                  key: "system",
                  label: (
                    <span className="tab-label">
                      <Server size={16} /> 系统
                    </span>
                  ),
                  children: <SystemView system={system} />
                }
              ]}
            />
          </Spin>

          <RobotDetailDrawer
            robot={selectedRobot}
            alerts={alerts}
            open={selectedRobot !== null}
            onClose={() => setSelectedRobot(null)}
          />
        </Content>
      </Layout>
    </ConfigProvider>
  );
}
