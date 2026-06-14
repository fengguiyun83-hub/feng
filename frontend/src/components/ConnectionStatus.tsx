import { Alert, Badge, Button, Space } from "antd";
import type { SseConnectionState } from "../types";

interface ConnectionStatusProps {
  state: SseConnectionState;
  disconnectedSince: number | null;
  onReconnect: () => void;
}

export function ConnectionStatus({ state, disconnectedSince, onReconnect }: ConnectionStatusProps) {
  if (state === "connected") {
    return <Badge status="success" text="实时连接正常" />;
  }

  if (state === "connecting") {
    return <Badge status="processing" text="实时连接中" />;
  }

  if (state === "reconnecting") {
    return <Badge status="warning" text="连接尝试中..." />;
  }

  const seconds = disconnectedSince ? Math.round((Date.now() - disconnectedSince) / 1000) : 0;
  return (
    <Space size={8}>
      <Badge status="error" text={`实时连接已断开${seconds ? ` ${seconds}s` : ""}`} />
      <Button size="small" danger onClick={onReconnect}>
        手动重连
      </Button>
    </Space>
  );
}

export function ConnectionAlert({ state, onReconnect }: Pick<ConnectionStatusProps, "state" | "onReconnect">) {
  if (state === "connected" || state === "connecting") {
    return null;
  }

  if (state === "reconnecting") {
    return (
      <Alert
        className="top-alert"
        type="warning"
        showIcon
        message="实时连接尝试中..."
        description="页面保留最后一次数据，EventSource 正在自动重连；如果后端刚重启，这是正常现象。"
      />
    );
  }

  return (
    <Alert
      className="top-alert"
      type="error"
      showIcon
      message="实时连接已断开"
      description="已超过 30 秒未恢复。请确认后端 API 正常运行，或点击手动重连。"
      action={
        <Button size="small" danger onClick={onReconnect}>
          手动重连
        </Button>
      }
    />
  );
}
