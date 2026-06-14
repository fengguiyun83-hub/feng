import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../api";
import type {
  DashboardStreamSnapshot,
  DashboardSummary,
  RobotAlert,
  RobotSnapshot,
  SseConnectionState,
  SystemStatus
} from "../types";

const DISCONNECTED_THRESHOLD_MS = 30_000;

export function useDashboardData() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [robots, setRobots] = useState<RobotSnapshot[]>([]);
  const [alerts, setAlerts] = useState<RobotAlert[]>([]);
  const [system, setSystem] = useState<SystemStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [connectionState, setConnectionState] = useState<SseConnectionState>("connecting");
  const [disconnectedSince, setDisconnectedSince] = useState<number | null>(null);
  const eventSourceRef = useRef<EventSource | null>(null);
  const disconnectTimerRef = useRef<number | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [summaryResult, robotsResult, alertsResult, systemResult] = await Promise.all([
        api.summary(),
        api.robots(),
        api.alerts(100),
        api.systemStatus()
      ]);
      setSummary(summaryResult);
      setRobots(robotsResult);
      setAlerts(alertsResult);
      setSystem(systemResult);
    } catch (err) {
      setError(err instanceof Error ? err.message : "后端 API 不可用");
    } finally {
      setLoading(false);
    }
  }, []);

  const clearDisconnectTimer = useCallback(() => {
    if (disconnectTimerRef.current !== null) {
      window.clearTimeout(disconnectTimerRef.current);
      disconnectTimerRef.current = null;
    }
  }, []);

  const connectStream = useCallback(() => {
    clearDisconnectTimer();
    eventSourceRef.current?.close();
    setConnectionState("connecting");
    setDisconnectedSince(null);

    const eventSource = new EventSource("/api/stream/dashboard");
    eventSourceRef.current = eventSource;

    eventSource.addEventListener("dashboard", (event) => {
      const snapshot = JSON.parse((event as MessageEvent<string>).data) as DashboardStreamSnapshot;
      setSummary(snapshot.summary);
      setRobots(snapshot.robots);
      setAlerts(snapshot.alerts);
      setSystem(snapshot.system);
      setConnectionState("connected");
      setDisconnectedSince(null);
      clearDisconnectTimer();
    });

    eventSource.onopen = () => {
      setConnectionState("connected");
      setDisconnectedSince(null);
      clearDisconnectTimer();
    };

    eventSource.onerror = () => {
      setConnectionState((current) => (current === "disconnected" ? current : "reconnecting"));
      setDisconnectedSince((current) => current ?? Date.now());
      clearDisconnectTimer();
      disconnectTimerRef.current = window.setTimeout(() => {
        setConnectionState("disconnected");
      }, DISCONNECTED_THRESHOLD_MS);
    };
  }, [clearDisconnectTimer]);

  const reconnectStream = useCallback(() => {
    connectStream();
  }, [connectStream]);

  const acknowledgeAlert = useCallback(async (alertId: string, note?: string) => {
    await api.acknowledgeAlert(alertId, { operator: "运维人员", note });
    await loadData();
  }, [loadData]);

  const closeAlert = useCallback(async (alertId: string, note: string) => {
    await api.closeAlert(alertId, { operator: "运维人员", note });
    await loadData();
  }, [loadData]);

  useEffect(() => {
    void loadData();
    connectStream();

    return () => {
      clearDisconnectTimer();
      eventSourceRef.current?.close();
    };
  }, [clearDisconnectTimer, connectStream, loadData]);

  return {
    summary,
    robots,
    alerts,
    system,
    loading,
    error,
    connectionState,
    disconnectedSince,
    refresh: loadData,
    reconnectStream,
    acknowledgeAlert,
    closeAlert
  };
}
