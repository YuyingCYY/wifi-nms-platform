import React, { useState } from 'react';
import { nmsApi } from '../api/nmsApi';
import type { DeviceRegisterPayload, DeviceType } from '../types/nms';

interface Props {
  onClose: () => void;
  onRegistered: () => void;
}

export const RegisterDeviceModal: React.FC<Props> = ({ onClose, onRegistered }) => {
  const [form, setForm] = useState<DeviceRegisterPayload>({
    macAddress: 'AA:BB:CC:01:00:02',
    name: 'AP-Taoyuan3-Lab-02',
    type: 'AP',
    model: 'DWL-WiFi7-Pro',
    siteName: 'Taoyuan-Plant3',
    ipAddress: '10.10.20.12',
    firmwareVersion: '1.2.0',
  });
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await nmsApi.registerDevice(form);
      onRegistered();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : '註冊失敗');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>新增納管設備 (POST /api/v1/devices)</h3>
          <button className="btn-ghost" onClick={onClose}>✕</button>
        </div>

        <form onSubmit={handleSubmit} className="form-grid">
          <label>
            <span>MAC Address *</span>
            <input
              required
              value={form.macAddress}
              onChange={(e) => setForm({ ...form, macAddress: e.target.value })}
              placeholder="AA:BB:CC:01:00:02"
            />
          </label>
          <label>
            <span>設備名稱 *</span>
            <input
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </label>
          <label>
            <span>設備類型 *</span>
            <select
              value={form.type}
              onChange={(e) => setForm({ ...form, type: e.target.value as DeviceType })}
            >
              <option value="AP">AP (Access Point)</option>
              <option value="SWITCH">SWITCH</option>
              <option value="GATEWAY">GATEWAY</option>
            </select>
          </label>
          <label>
            <span>硬體型號 *</span>
            <input
              required
              value={form.model}
              onChange={(e) => setForm({ ...form, model: e.target.value })}
            />
          </label>
          <label>
            <span>所屬廠區 (Site) *</span>
            <input
              required
              value={form.siteName}
              onChange={(e) => setForm({ ...form, siteName: e.target.value })}
            />
          </label>
          <label>
            <span>管理 IP</span>
            <input
              value={form.ipAddress}
              onChange={(e) => setForm({ ...form, ipAddress: e.target.value })}
            />
          </label>
          <label className="full-width">
            <span>目前韌體版本 *</span>
            <input
              required
              value={form.firmwareVersion}
              onChange={(e) => setForm({ ...form, firmwareVersion: e.target.value })}
            />
          </label>

          {error && <div className="alert alert-error full-width">{error}</div>}

          <div className="modal-actions full-width">
            <button type="button" className="btn-secondary" onClick={onClose}>
              取消
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? '註冊中...' : '確認納管'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
