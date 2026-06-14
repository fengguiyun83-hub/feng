export type RobotStatus = "ONLINE" | "OFFLINE" | "WARNING" | "MAINTENANCE";
export type AlertStatus = "NEW" | "ACKNOWLEDGED" | "CLOSED";

export interface RobotSnapshot {
  robotId: string;
  name: string;
  model: string;
  location: string;
  status: RobotStatus;
  batteryPercent: number;
  temperatureCelsius: number;
  lastSeenAt: number;
  positionX: number;
  positionY: number;
  positionZ: number;
  yawRadians: number;
  linearVelocity: number;
  angularVelocity: number;
  motorCurrentAmp: number;
  telemetrySource: string;
}

export interface RobotAlert {
  id: string;
  robotId: string;
  avgTemperature: number;
  windowStart: number;
  windowEnd: number;
  eventCount: number;
  alertTime: number;
  payload: string;
  status: AlertStatus;
  acknowledgedAt?: number | null;
  acknowledgedBy?: string | null;
  closedAt?: number | null;
  closedBy?: string | null;
  operationNote?: string | null;
  updatedAt?: number | null;
}

export interface DashboardSummary {
  robotTotal: number;
  onlineRobots: number;
  warningRobots: number;
  maintenanceRobots: number;
  averageTemperature: number;
  redisAlertCount: number;
  openAlertCount: number;
}

export interface ComponentStatus {
  name: string;
  status: string;
  detail: string;
}

export interface SystemStatus {
  status: string;
  components: ComponentStatus[];
}

export interface DashboardStreamSnapshot {
  summary: DashboardSummary;
  robots: RobotSnapshot[];
  alerts: RobotAlert[];
  system: SystemStatus;
  timestamp: number;
}

export type SseConnectionState = "connecting" | "connected" | "reconnecting" | "disconnected";
