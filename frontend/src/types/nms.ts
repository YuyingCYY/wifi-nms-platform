export type DeviceType = 'AP' | 'SWITCH' | 'GATEWAY';

export type OtaStatus = 'PENDING' | 'IN_PROGRESS' | 'SUCCESS' | 'FAILED';

export interface Device {
  id: number;
  macAddress: string;
  name: string;
  type: DeviceType;
  model: string;
  siteName: string;
  ipAddress: string | null;
  firmwareVersion: string;
  configJson: string;
  online: boolean;
  heartbeatTtlSeconds: number;
  lastSeenAt: string | null;
}

export interface DeviceRegisterPayload {
  macAddress: string;
  name: string;
  type: DeviceType;
  model: string;
  siteName: string;
  ipAddress: string;
  firmwareVersion: string;
}

export interface DeviceTelemetry {
  id: string;
  macAddress: string;
  deviceType: string;
  cpuUsage: number | null;
  memoryUsage: number | null;
  temperatureC: number | null;
  metrics: Record<string, string | number | boolean>;
  timestamp: string;
}

export interface DeviceEvent {
  id: string;
  macAddress: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL' | string;
  eventType: string;
  message: string;
  details: Record<string, unknown>;
  timestamp: string;
}

export interface OtaTask {
  id: number;
  taskId: string;
  macAddress: string;
  targetVersion: string;
  firmwareUrl: string;
  sha256Checksum: string | null;
  status: OtaStatus;
  message: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface OtaUpgradePayload {
  targetVersion: string;
  firmwareUrl: string;
  sha256Checksum?: string;
}
