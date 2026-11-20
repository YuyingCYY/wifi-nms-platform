# 面試 5 分鐘實機 Demo 腳本

如果面試官對你的實作有興趣，或你想在結尾主動加分展示，可以照這個 5 分鐘節奏進行：

### 0:00 - 1:00｜架構開場（看 README 架構圖）
* **重點話術**：「針對貴部門 AP/Switch/Gateway 的管理場景，我做了一個小型的後端原型。核心想展現的是 **資料分層儲存（PG + Mongo + Redis）** 與 **MQTT 雙向設備通訊**。」
* 點出三個設計重點：
  1. 心跳走 Redis TTL，不打爆 DB。
  2. 異質設備的 Telemetry 與告警走 MongoDB + TTL Index 自動清理。
  3. OTA 升級透過 Redis 分散式鎖防止多 Pod 環境下的併發重複下發。

### 1:00 - 2:30｜展示設備即時連線與組態下發
* 打開終端機，已預先跑好 `docker compose up`。
* 打開瀏覽器 `http://localhost:8080/swagger-ui.html`（比純 curl 更直觀）。
* 呼叫 `GET /api/v1/devices`：展示 3 台設備（AP、Switch、Gateway）都在線，且能看到 `heartbeatTtlSeconds` 在倒數並隨心跳刷新。
* 呼叫 `POST /api/v1/devices/AA:BB:CC:01:00:01/config` 下發 VLAN 10 / WPA3-802.1X 設定，切到 terminal 秀出模擬 AP 即時收到指令並回報 `CONFIG_APPLIED`。

### 2:30 - 4:00｜展示 OTA 升級與分散式鎖防護
* 呼叫 `POST /api/v1/devices/AA:BB:CC:01:00:01/ota` 升版到 `1.3.0`。
* **立刻再按一次 Execute**：秀出 `409 Conflict` 擋下重複請求（強調這在 K8s 多副本環境中非常重要）。
* 5 秒後查詢 `GET /api/v1/devices/AA:BB:CC:01:00:01/ota` 與 `GET /api/v1/devices`，版本號已自動變為 `1.3.0`。

### 4:00 - 5:00｜秀 K8s/K3s 配置與收尾
* 簡單打開 `k8s/02-deployment.yaml`，點出自己已把 `readinessProbe`、`livenessProbe`、`preStop` 優雅停機、以及 `resources.limits` 搭配 JVM `-XX:MaxRAMPercentage=75.0` 納入考量，呼應 Production 維運思維。
