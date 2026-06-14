import { Empty, Space, Tag, Typography } from "antd";
import type { ComponentStatus, SystemStatus } from "../types";

const { Text } = Typography;

export function SystemView({ system }: { system: SystemStatus | null }) {
  if (!system) {
    return (
      <section className="panel">
        <Empty description="暂无系统状态" />
      </section>
    );
  }

  return (
    <section className="panel system-grid">
      {system.components.map((component: ComponentStatus) => (
        <div className="system-item" key={component.name}>
          <Space direction="vertical" size={4}>
            <Text strong>{component.name}</Text>
            <Tag color={component.status === "UP" ? "success" : component.status === "DOWN" ? "error" : "processing"}>
              {component.status}
            </Tag>
            <Text type="secondary">{component.detail}</Text>
          </Space>
        </div>
      ))}
    </section>
  );
}
