import type { AlertStatus, DashboardSummary, RobotAlert, RobotSnapshot, SystemStatus } from "./types";

async function requestJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: "application/json" }
  });

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<T>;
}

export const api = {
  summary: () => requestJson<DashboardSummary>("/api/dashboard/summary"),
  robots: (params?: { status?: string; keyword?: string }) => {
    const search = new URLSearchParams();
    if (params?.status) search.set("status", params.status);
    if (params?.keyword) search.set("keyword", params.keyword);
    const query = search.toString();
    return requestJson<RobotSnapshot[]>(`/api/robots${query ? `?${query}` : ""}`);
  },
  alerts: (limit = 100, status?: AlertStatus | "ALL") => {
    const search = new URLSearchParams({ limit: String(limit) });
    if (status && status !== "ALL") search.set("status", status);
    return requestJson<RobotAlert[]>(`/api/alerts?${search.toString()}`);
  },
  acknowledgeAlert: (alertId: string, body: { operator?: string; note?: string }) =>
    postJson<RobotAlert>(`/api/alerts/${encodeURIComponent(alertId)}/acknowledge`, body),
  closeAlert: (alertId: string, body: { operator?: string; note: string }) =>
    postJson<RobotAlert>(`/api/alerts/${encodeURIComponent(alertId)}/close`, body),
  systemStatus: () => requestJson<SystemStatus>("/api/system/status")
};
