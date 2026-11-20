import React, { useCallback, useEffect, useState } from 'react';
import { nmsApi } from './api/nmsApi';
import { DeviceDetailPanel } from './components/DeviceDetailPanel';
import { RegisterDeviceModal } from './components/RegisterDeviceModal';
import type { Device, DeviceEvent, DeviceType } from './types/nms';

export const App: React.FC = () => {
  const [devices, setDevices] = useState<Device[]>([]);
  const [events, setEvents] = useState<DeviceEvent[]>([]);
  const [typeFilter, setTypeFilter] = useState<DeviceType | 'ALL'>('ALL');
  const [selectedMac, setSelectedMac] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [showRegister, setShowRegister] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<string>('');

  const fetchAll = useCallback(async () => {
    try {
      const [devList, evtList] = await Promise.all([
        nmsApi.listDevices(typeFilter),
        nmsApi.getRecentEvents(15),
      ]);
      setDevices(devList);
      setEvents(evtList);
      setError(null);
      setLastUpdated(new Date().toLocaleTimeString());

      if (devList.length > 0 && !devList.some((d) => d.macAddress === selectedMac)) {
        setSelectedMac(devList[0].macAddress);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '無法連線至後端 API');
    }
  }, [typeFilter, selectedMac]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = setInterval(fetchAll, 5000);
    return () => clearInterval(timer);
  }, [autoRefresh, fetchAll]);

  const onlineCount = devices.filter((d) => d.online).length;
  const offlineCount = devices.length - onlineCount;
  const alertCount = events.filter(
    (e) => e.severity === 'WARNING' || e.severity === 'CRITICAL'
  ).length;

  const selectedDevice = devices.find((d) => d.macAddress === selectedMac) ?? null;

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-left">
          <h1>Wi-Fi NMS Console</h1>
          <span className="topbar-sub">AP / Switch / Gateway 集中管理平台</span>
        </div>
        <div className="topbar-actions">
          <label className="toggle-label">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
            />
            自動更新 (5s)
          </label>
          <button className="btn-secondary btn-sm" onClick={fetchAll}>
            立即刷新
          </button>
          <button className="btn-primary btn-sm" onClick={() => setShowRegister(true)}>
            + 新增設備
          </button>
          <a
            className="btn-ghost-light btn-sm"
            href="http://localhost:8080/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
          >
            Swagger API ↗
          </a>
        </div>
      </header>

      <main className="main-content">
        {error && <div className="alert alert-error">{error}</div>}

        {/* KPI Summary Cards */}
        <section className="kpi-grid">
          <div className="kpi-card">
            <span>納管設備總數</span>
            <strong>{devices.length}</strong>
          </div>
          <div className="kpi-card kpi-green">
            <span>在線設備 (Redis TTL)</span>
            <strong>{onlineCount}</strong>
          </div>
          <div className="kpi-card kpi-red">
            <span>離線設備</span>
            <strong>{offlineCount}</strong>
          </div>
          <div className="kpi-card kpi-amber">
            <span>近期異常告警</span>
            <strong>{alertCount}</strong>
          </div>
        </section>

        {/* Main Workspace: Device List + Selected Device Panel */}
        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-header">
              <div>
                <h2>設備清單 (PostgreSQL + Redis)</h2>
                <p className="muted-text">點選設備檢視監控數據、下發設定或執行 OTA（上次更新：{lastUpdated || '-'}）</p>
              </div>
              <div className="filter-group">
                {(['ALL', 'AP', 'SWITCH', 'GATEWAY'] as const).map((t) => (
                  <button
                    key={t}
                    className={typeFilter === t ? 'chip active' : 'chip'}
                    onClick={() => setTypeFilter(t)}
                  >
                    {t}
                  </button>
                ))}
              </div>
            </div>

            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>狀態 / TTL</th>
                    <th>設備名稱</th>
                    <th>類型</th>
                    <th>IP / MAC</th>
                    <th>韌體</th>
                  </tr>
                </thead>
                <tbody>
                  {devices.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="empty-cell">
                        尚無符合條件的設備
                      </td>
                    </tr>
                  ) : (
                    devices.map((dev) => (
                      <tr
                        key={dev.macAddress}
                        className={selectedMac === dev.macAddress ? 'row-selected' : 'row-hover'}
                        onClick={() => setSelectedMac(dev.macAddress)}
                      >
                        <td>
                          <span className={`badge ${dev.online ? 'badge-online' : 'badge-offline'}`}>
                            {dev.online ? `ONLINE (${dev.heartbeatTtlSeconds}s)` : 'OFFLINE'}
                          </span>
                        </td>
                        <td>
                          <div className="cell-main">{dev.name}</div>
                          <div className="cell-sub">{dev.siteName} · {dev.model}</div>
                        </td>
                        <td>
                          <span className={`badge badge-type-${dev.type.toLowerCase()}`}>
                            {dev.type}
                          </span>
                        </td>
                        <td>
                          <div className="cell-main"><code>{dev.ipAddress ?? '-'}</code></div>
                          <div className="cell-sub"><code>{dev.macAddress}</code></div>
                        </td>
                        <td>v{dev.firmwareVersion}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {selectedDevice ? (
            <DeviceDetailPanel device={selectedDevice} onActionSuccess={fetchAll} />
          ) : (
            <div className="panel empty-panel">請從左側點選一台設備進行操作與監控</div>
          )}
        </section>

        {/* System-wide Recent Events from MongoDB */}
        <section className="panel">
          <div className="panel-header">
            <div>
              <h2>即時事件與告警日誌 (MongoDB TTL Collection)</h2>
              <p className="muted-text">包含組態套用回報、OTA 執行結果、CPU/溫度閾值異常告警</p>
            </div>
          </div>
          <div className="table-wrap">
            <table className="data-table compact">
              <thead>
                <tr>
                  <th>時間</th>
                  <th>等級</th>
                  <th>設備 MAC</th>
                  <th>事件類型</th>
                  <th>訊息內容</th>
                </tr>
              </thead>
              <tbody>
                {events.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="empty-cell">尚無事件紀錄</td>
                  </tr>
                ) : (
                  events.map((ev) => (
                    <tr key={ev.id}>
                      <td>{new Date(ev.timestamp).toLocaleTimeString()}</td>
                      <td>
                        <span className={`badge badge-sev-${ev.severity.toLowerCase()}`}>
                          {ev.severity}
                        </span>
                      </td>
                      <td><code>{ev.macAddress}</code></td>
                      <td><code>{ev.eventType}</code></td>
                      <td>{ev.message}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </main>

      {showRegister && (
        <RegisterDeviceModal
          onClose={() => setShowRegister(false)}
          onRegistered={fetchAll}
        />
      )}
    </div>
  );
};
