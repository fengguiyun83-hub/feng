import type { RobotStatus } from "./types";

export const statusLabels: Record<RobotStatus, string> = {
  ONLINE: "在线",
  OFFLINE: "离线",
  WARNING: "告警",
  MAINTENANCE: "维护"
};

export const statusColors: Record<RobotStatus, string> = {
  ONLINE: "success",
  OFFLINE: "default",
  WARNING: "warning",
  MAINTENANCE: "processing"
};

export function formatTime(value: number) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(value));
}

export function formatDateTime(value: number) {
  if (!value) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit"
  }).format(new Date(value));
}
