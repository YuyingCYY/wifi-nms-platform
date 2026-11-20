import React, { useEffect, useState } from 'react';
import { nmsApi } from '../api/nmsApi';
import type { Device, DeviceTelemetry, OtaTask } from '../types/nms';

interface Props {
  device: Device;
  onActionSuccess: () => void;
}

type TabKey = 'telemetry' | 'config' | 'ota';

const DEFAULT_CONFIG_BY_TYPE: Record<string, Record<string, unknown>> = {
  AP: {
    ssid: 'Delta-Corp-WiFi7',
    vlanId: 10,
    authMode: 'WPA3-802.1X',
    radiusServer: '10.10.1.50',
  },
  SWITCH: {
    trunkPorts: ['eth0', 'eth1'],
    allowedVlans: [10, 20, 30],
    poeEnabled: true,
  },
  GATEWAY: {
    dhcpRange: '10.10.20.100-200',
    dnsServers: ['8.8.8.8', '1.1.1.1'],
    natEnabled: true,
  },
};

const MiniTrendChart: React.FC<{ data: DeviceTelemetry[] }> = ({ data }) => {
  if (data.length < 2) {
    return <p className="muted-text">等待累積至少 2 筆 Telemetry 數據即可繪製趨勢圖...</p>;
  }

  const ordered = [...data].reverse();
  const width = 460;
  const height = 110;
  const pad = 12;

  const toPoints = (getter: (t: DeviceTelemetry) => number | null) =>
    ordered
      .map((item, idx) => {
        const val = getter(item) ?? 0;
        const x = pad + (idx / (ordered.length - 1)) * (width - pad * 2);
        const y = height - pad - (Math.min(Math.max(val, 0), 100) / 100) * (height - pad * 2);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');

  return (
    <div className="chart-box">
      <div className="chart-legend">
        <span><i className="dot dot-blue" /> CPU (%)</span>
        <span><i className="dot dot-amber" /> 溫度 (°C)</span>
      </div>
      <svg viewBox={`0 0 ${width} ${height}`} className="trend-svg">
        <line x1={pad} y1={height - pad} x2={width - pad} y2={height - pad} stroke="#e2e8f0" />
        <line x1={pad} y1={pad} x2={width - pad} y2={pad} stroke="#e2e8f0" strokeDasharray="3 3" />
        <polyline fill="none" stroke="#2563eb" strokeWidth="2.2" points={toPoints((t) => t.cpuUsage)} />
        <polyline fill="none" stroke="#d97706" strokeWidth="2.2" points={toPoints((t) => t.temperatureC)} />
      </svg>
    </div>
  );
};

export const DeviceDetailPanel: React.FC<Props> = ({ device, onActionSuccess }) => {
  const [activeTab, setActiveTab] = useState<TabKey>('telemetry');
  const [telemetry, setTelemetry] = useState<DeviceTelemetry[]>([]);
  const [otaTasks, setOtaTasks] = useState<OtaTask[]>([]);

  // Config form state
  const [configText, setConfigText] = useState('');
  const [configMsg, setConfigMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);
  const [configBusy, setConfigBusy] = useState(false);

  // OTA form state
  const [targetVersion, setTargetVersion] = useState('1.3.0');
  const [firmwareUrl, setFirmwareUrl] = useState('https://fw.delta.local/firmware/v1.3.0.bin');
  const [checksum, setChecksum] = useState('e3b0c44298fc1c149afbf4c8996fb924');
  const [otaMsg, setOtaMsg] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);
  const [otaBusy, setOtaBusy] = useState(false);

  const loadDetails = async () => {
    try {
      const [tel, tasks] = await Promise.all([
        nmsApi.getTelemetry(device.macAddress, 15),
        nmsApi.getOtaHistory(device.macAddress),
      ]);
      setTelemetry(tel);
      setOtaTasks(tasks);
    } catch {
      // handled silently on background refresh
    }
  };

  useEffect(() => {
    setConfigMsg(null);
    setOtaMsg(null);
    const hasExisting = device.configJson && device.configJson !== '{}';
    if (hasExisting) {
      try {
        setConfigText(JSON.stringify(JSON.parse(device.configJson), null, 2));
      } catch {
        setConfigText(device.configJson);
      }
    } else {
      setConfigText(JSON.stringify(DEFAULT_CONFIG_BY_TYPE[device.type] ?? {}, null, 2));
    }

    const nextMinor = bumpVersion(device.firmwareVersion);
    setTargetVersion(nextMinor);
    setFirmwareUrl(`https://fw.delta.local/${device.type.toLowerCase()}/v${nextMinor}.bin`);
    loadDetails();
  }, [device.macAddress]);

  useEffect(() => {
    const timer = setInterval(loadDetails, 5000);
    return () => clearInterval(timer);
  }, [device.macAddress]);

  const handleApplyConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    setConfigBusy(true);
    setConfigMsg(null);
    try {
      const parsed = JSON.parse(configText);
      await nmsApi.applyConfig(device.macAddress, parsed);
      setConfigMsg({ type: 'ok', text: '組態已寫入 PostgreSQL 並透過 MQTT 下發至設備！' });
      onActionSuccess();
    } catch (err) {
      setConfigMsg({
        type: 'err',
        text: err instanceof Error ? err.message : 'JSON 格式或下發失敗',
      });
    } finally {
      setConfigBusy(false);
    }
  };

  const handleTriggerOta = async (e: React.FormEvent) => {
    e.preventDefault();
    setOtaBusy(true);
    setOtaMsg(null);
    try {
      const task = await nmsApi.triggerOta(device.macAddress, {
        targetVersion,
        firmwareUrl,
        sha256Checksum: checksum,
      });
      setOtaMsg({
        type: 'ok',
        text: `OTA 任務 ${task.taskId} 已透過 MQTT 下發（模擬設備約 5 秒回報結果；5 秒內再點一次可測試 Redis 分散式鎖 409 Conflict）`,
      });
      await loadDetails();
      onActionSuccess();
    } catch (err) {
      setOtaMsg({
        type: 'err',
        text: err instanceof Error ? err.message : '觸發 OTA 失敗',
      });
    } finally {
      setOtaBusy(false);
    }
  };

  const latest = telemetry[0];

  return (
    <div className="panel">
      <div className="panel-header">
        <div>
          <div className="title-row">
            <h2>{device.name}</h2>
            <span className={`badge badge-type-${device.type.toLowerCase()}`}>{device.type}</span>
            <span className={`badge ${device.online ? 'badge-online' : 'badge-offline'}`}>
              {device.online ? `ONLINE (TTL ${device.heartbeatTtlSeconds}s)` : 'OFFLINE'}
            </span>
          </div>
          <p className="muted-text">
            MAC: <code>{device.macAddress}</code> · IP: <code>{device.ipAddress ?? '-'}</code> ·
            Model: {device.model} · FW: <strong>v{device.firmwareVersion}</strong>
          </p>
        </div>
      </div>

      <div className="tabs">
        <button
          className={activeTab === 'telemetry' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('telemetry')}
        >
          即時監控 (MongoDB)
        </button>
        <button
          className={activeTab === 'config' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('config')}
        >
          組態下發 (MQTT Config)
        </button>
        <button
          className={activeTab === 'ota' ? 'tab active' : 'tab'}
          onClick={() => setActiveTab('ota')}
        >
          OTA 韌體升級 (Redis Lock)
        </button>
      </div>

      {activeTab === 'telemetry' && (
        <div className="tab-body">
          {latest ? (
            <>
              <div className="metric-grid">
                <div className="metric-card">
                  <span>CPU 使用率</span>
                  <strong>{latest.cpuUsage?.toFixed(1) ?? '-'}%</strong>
                </div>
                <div className="metric-card">
                  <span>記憶體使用率</span>
                  <strong>{latest.memoryUsage?.toFixed(1) ?? '-'}%</strong>
                </div>
                <div className="metric-card">
                  <span>設備溫度</span>
                  <strong>{latest.temperatureC?.toFixed(1) ?? '-'} °C</strong>
                </div>
                {Object.entries(latest.metrics ?? {}).map(([k, v]) => (
                  <div className="metric-card" key={k}>
                    <span>{k}</span>
                    <strong>{String(v)}</strong>
                  </div>
                ))}
              </div>
              <MiniTrendChart data={telemetry} />
            </>
          ) : (
            <p className="muted-text">尚無此設備的 Telemetry 監控數據。</p>
          )}
        </div>
      )}

      {activeTab === 'config' && (
        <form onSubmit={handleApplyConfig} className="tab-body">
          <p className="muted-text">
            編輯 JSON 後點擊下發，後端將更新 PostgreSQL 並發佈至 MQTT Topic{' '}
            <code>nms/devices/{device.macAddress}/command</code>。
          </p>
          <textarea
            className="code-editor"
            rows={8}
            value={configText}
            onChange={(e) => setConfigText(e.target.value)}
          />
          {configMsg && (
            <div className={`alert ${configMsg.type === 'ok' ? 'alert-success' : 'alert-error'}`}>
              {configMsg.text}
            </div>
          )}
          <div className="action-row">
            <button
              type="button"
              className="btn-secondary"
              onClick={() =>
                setConfigText(JSON.stringify(DEFAULT_CONFIG_BY_TYPE[device.type] ?? {}, null, 2))
              }
            >
              載入 {device.type} 預設範本
            </button>
            <button type="submit" className="btn-primary" disabled={configBusy}>
              {configBusy ? '下發中...' : '下發組態至設備'}
            </button>
          </div>
        </form>
      )}

      {activeTab === 'ota' && (
        <div className="tab-body">
          <form onSubmit={handleTriggerOta} className="form-grid">
            <label>
              <span>目標韌體版本 *</span>
              <input
                required
                value={targetVersion}
                onChange={(e) => setTargetVersion(e.target.value)}
              />
            </label>
            <label>
              <span>SHA-256 校驗碼</span>
              <input value={checksum} onChange={(e) => setChecksum(e.target.value)} />
            </label>
            <label className="full-width">
              <span>韌體下載 URL *</span>
              <input
                required
                value={firmwareUrl}
                onChange={(e) => setFirmwareUrl(e.target.value)}
              />
            </label>
            {otaMsg && (
              <div
                className={`alert full-width ${
                  otaMsg.type === 'ok' ? 'alert-success' : 'alert-error'
                }`}
              >
                {otaMsg.text}
              </div>
            )}
            <div className="action-row full-width">
              <button type="submit" className="btn-primary" disabled={otaBusy}>
                {otaBusy ? '下發中...' : '觸發 OTA 韌體升級'}
              </button>
            </div>
          </form>

          <h4 className="section-subtitle">OTA 任務歷史紀錄 (PostgreSQL)</h4>
          {otaTasks.length === 0 ? (
            <p className="muted-text">尚無 OTA 升級紀錄。</p>
          ) : (
            <table className="data-table compact">
              <thead>
                <tr>
                  <th>Task ID</th>
                  <th>目標版本</th>
                  <th>狀態</th>
                  <th>說明</th>
                  <th>建立時間</th>
                </tr>
              </thead>
              <tbody>
                {otaTasks.map((t) => (
                  <tr key={t.taskId}>
                    <td><code>{t.taskId}</code></td>
                    <td>v{t.targetVersion}</td>
                    <td>
                      <span className={`badge badge-ota-${t.status.toLowerCase()}`}>
                        {t.status}
                      </span>
                    </td>
                    <td>{t.message ?? '-'}</td>
                    <td>{new Date(t.createdAt).toLocaleTimeString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
};

function bumpVersion(ver: string): string {
  const parts = ver.split('.').map((n) => parseInt(n, 10));
  if (parts.length === 3 && parts.every((n) => !Number.isNaN(n))) {
    return `${parts[0]}.${parts[1] + 1}.0`;
  }
  return `${ver}-next`;
}
