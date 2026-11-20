import type {
  Device,
  DeviceEvent,
  DeviceRegisterPayload,
  DeviceTelemetry,
  DeviceType,
  OtaTask,
  OtaUpgradePayload,
} from '../types/nms';

const API_BASE = '/api/v1';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const errBody = await response.json();
      if (errBody?.message) {
        message = `${response.status}: ${errBody.message}`;
      }
    } catch {
      // keep default HTTP status message
    }
    throw new Error(message);
  }

  return (await response.json()) as T;
}

export const nmsApi = {
  listDevices(type?: DeviceType | 'ALL'): Promise<Device[]> {
    const query = type && type !== 'ALL' ? `?type=${encodeURIComponent(type)}` : '';
    return request<Device[]>(`/devices${query}`);
  },

  registerDevice(payload: DeviceRegisterPayload): Promise<Device> {
    return request<Device>('/devices', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  applyConfig(macAddress: string, config: Record<string, unknown>): Promise<Device> {
    return request<Device>(`/devices/${encodeURIComponent(macAddress)}/config`, {
      method: 'POST',
      body: JSON.stringify({ config }),
    });
  },

  getTelemetry(macAddress: string, limit = 15): Promise<DeviceTelemetry[]> {
    return request<DeviceTelemetry[]>(
      `/devices/${encodeURIComponent(macAddress)}/telemetry?limit=${limit}`
    );
  },

  getRecentEvents(limit = 20): Promise<DeviceEvent[]> {
    return request<DeviceEvent[]>(`/devices/events/recent?limit=${limit}`);
  },

  triggerOta(macAddress: string, payload: OtaUpgradePayload): Promise<OtaTask> {
    return request<OtaTask>(`/devices/${encodeURIComponent(macAddress)}/ota`, {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  },

  getOtaHistory(macAddress: string): Promise<OtaTask[]> {
    return request<OtaTask[]>(`/devices/${encodeURIComponent(macAddress)}/ota`);
  },
};
