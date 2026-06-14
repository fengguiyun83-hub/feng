import { Button, Empty, Form, Modal, Select, Space, Table, Tag, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useState } from "react";
import { formatDateTime, formatTime } from "../constants";
import type { AlertStatus, RobotAlert } from "../types";

interface AlertCenterViewProps {
  alerts: RobotAlert[];
  onAcknowledge: (alertId: string, note?: string) => Promise<void>;
  onCloseAlert: (alertId: string, note: string) => Promise<void>;
}

type ModalAction = "acknowledge" | "close";

const statusLabels: Record<AlertStatus, string> = {
  NEW: "新告警",
  ACKNOWLEDGED: "已确认",
  CLOSED: "已关闭"
};

const statusColors: Record<AlertStatus, string> = {
  NEW: "orange",
  ACKNOWLEDGED: "blue",
  CLOSED: "green"
};

export function AlertCenterView({ alerts, onAcknowledge, onCloseAlert }: AlertCenterViewProps) {
  const [statusFilter, setStatusFilter] = useState<AlertStatus | "ALL">("ALL");
  const [activeAlert, setActiveAlert] = useState<RobotAlert | null>(null);
  const [modalAction, setModalAction] = useState<ModalAction>("acknowledge");
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<{ note: string }>();

  const filteredAlerts = useMemo(
    () => alerts.filter((alert) => statusFilter === "ALL" || alert.status === statusFilter),
    [alerts, statusFilter]
  );

  const openModal = (alert: RobotAlert, action: ModalAction) => {
    setActiveAlert(alert);
    setModalAction(action);
    form.resetFields();
  };

  const handleSubmit = async () => {
    if (!activeAlert) return;
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      if (modalAction === "acknowledge") {
        await onAcknowledge(activeAlert.id, values.note);
        message.success("告警已确认");
      } else {
        await onCloseAlert(activeAlert.id, values.note);
        message.success("告警已关闭");
      }
      setActiveAlert(null);
    } finally {
      setSubmitting(false);
    }
  };

  const alertColumns: ColumnsType<RobotAlert> = [
    { title: "告警 ID", dataIndex: "id", key: "id", ellipsis: true },
    { title: "机器人", dataIndex: "robotId", key: "robotId" },
    {
      title: "状态",
      dataIndex: "status",
      key: "status",
      render: (status: AlertStatus) => <Tag color={statusColors[status]}>{statusLabels[status]}</Tag>
    },
    {
      title: "等级",
      key: "level",
      render: (_, record) => {
        const critical = record.avgTemperature >= 45;
        return <Tag color={critical ? "volcano" : "orange"}>{critical ? "CRITICAL" : "WARNING"}</Tag>;
      }
    },
    {
      title: "平均温度",
      dataIndex: "avgTemperature",
      key: "avgTemperature",
      sorter: (a, b) => a.avgTemperature - b.avgTemperature,
      render: (value: number) => <Tag color={value >= 45 ? "volcano" : "orange"}>{value.toFixed(2)} °C</Tag>
    },
    { title: "事件数", dataIndex: "eventCount", key: "eventCount" },
    { title: "窗口开始", dataIndex: "windowStart", key: "windowStart", render: formatTime },
    { title: "告警时间", dataIndex: "alertTime", key: "alertTime", render: formatDateTime },
    {
      title: "操作",
      key: "actions",
      fixed: "right",
      render: (_, record) => (
        <Space>
          {record.status === "NEW" && (
            <Button size="small" onClick={() => openModal(record, "acknowledge")}>
              确认告警
            </Button>
          )}
          {record.status !== "CLOSED" && (
            <Button size="small" type="primary" onClick={() => openModal(record, "close")}>
              关闭告警
            </Button>
          )}
          {record.status === "CLOSED" && <Tag color="green">已完成</Tag>}
        </Space>
      )
    }
  ];

  return (
    <section className="panel">
      <div className="table-toolbar">
        <Select
          value={statusFilter}
          onChange={setStatusFilter}
          style={{ width: 160 }}
          options={[
            { value: "ALL", label: "全部告警" },
            { value: "NEW", label: "新告警" },
            { value: "ACKNOWLEDGED", label: "已确认" },
            { value: "CLOSED", label: "已关闭" }
          ]}
        />
      </div>
      <Table
        rowKey="id"
        columns={alertColumns}
        dataSource={filteredAlerts}
        locale={{ emptyText: <Empty description="暂无告警记录" /> }}
        pagination={{ pageSize: 10, showSizeChanger: false }}
        scroll={{ x: 1100 }}
      />
      <Modal
        title={modalAction === "acknowledge" ? "确认告警" : "关闭告警"}
        open={activeAlert !== null}
        confirmLoading={submitting}
        okText={modalAction === "acknowledge" ? "确认" : "关闭"}
        cancelText="取消"
        onOk={() => void handleSubmit()}
        onCancel={() => setActiveAlert(null)}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            label="运维备注"
            name="note"
            rules={modalAction === "close" ? [{ required: true, whitespace: true, message: "关闭告警必须填写运维备注" }] : []}
          >
            <textarea className="ant-input" rows={4} placeholder={modalAction === "close" ? "填写处理结论" : "可填写确认说明"} />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}
