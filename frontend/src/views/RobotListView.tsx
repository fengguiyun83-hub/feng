import { Empty, Input, Progress, Segmented, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { formatDateTime, statusColors, statusLabels } from "../constants";
import type { RobotSnapshot, RobotStatus } from "../types";

const { Text } = Typography;

interface RobotListViewProps {
  robots: RobotSnapshot[];
  statusFilter: string;
  keyword: string;
  onStatusFilterChange: (value: string) => void;
  onKeywordChange: (value: string) => void;
  onSelectRobot: (robot: RobotSnapshot) => void;
}

export function RobotListView({
  robots,
  statusFilter,
  keyword,
  onStatusFilterChange,
  onKeywordChange,
  onSelectRobot
}: RobotListViewProps) {
  const robotColumns: ColumnsType<RobotSnapshot> = [
    {
      title: "机器人",
      dataIndex: "name",
      key: "name",
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text strong>{record.name}</Text>
          <Text type="secondary">{record.robotId}</Text>
        </Space>
      )
    },
    { title: "型号", dataIndex: "model", key: "model" },
    { title: "位置", dataIndex: "location", key: "location" },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: RobotStatus) => <Tag color={statusColors[status]}>{statusLabels[status]}</Tag>
    },
    {
      title: "温度",
      dataIndex: "temperatureCelsius",
      key: "temperatureCelsius",
      sorter: (a, b) => a.temperatureCelsius - b.temperatureCelsius,
      render: (value: number) => `${value.toFixed(2)} °C`
    },
    {
      title: "电量",
      dataIndex: "batteryPercent",
      key: "batteryPercent",
      sorter: (a, b) => a.batteryPercent - b.batteryPercent,
      render: (value: number) => <Progress percent={Math.round(value)} size="small" />
    },
    {
      title: "最后上报",
      dataIndex: "lastSeenAt",
      key: "lastSeenAt",
      render: formatDateTime
    }
  ];

  return (
    <section className="panel">
      <div className="panel-toolbar">
        <Segmented
          value={statusFilter}
          onChange={(value) => onStatusFilterChange(String(value))}
          options={[
            { label: "全部", value: "ALL" },
            { label: "在线", value: "ONLINE" },
            { label: "告警", value: "WARNING" },
            { label: "维护", value: "MAINTENANCE" },
            { label: "离线", value: "OFFLINE" }
          ]}
        />
        <Input.Search
          allowClear
          placeholder="搜索编号、名称或位置"
          value={keyword}
          onChange={(event) => onKeywordChange(event.target.value)}
          className="search-input"
        />
      </div>
      <Table
        rowKey="robotId"
        columns={robotColumns}
        dataSource={robots}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        locale={{ emptyText: <Empty description="暂无机器人" /> }}
        onRow={(record) => ({
          onClick: () => onSelectRobot(record)
        })}
        rowClassName="clickable-row"
      />
    </section>
  );
}
