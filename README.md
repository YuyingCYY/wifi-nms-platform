# Wi-Fi Network Management System (NMS) — Backend Demo

一套基於 **Java 17 / Spring Boot 3** 實作的網通設備集中管理系統原型，模擬企業/廠區場域中 **Access Point (AP)、Switch、Edge Gateway** 的納管、即時狀態監控、組態下發（VLAN / SSID / 802.1X）與 OTA 韌體升級流程。

---

## 1. 系統架構 (System Architecture)

```text
  [ AP / Switch / Gateway 設備端 ] (本專案提供 Python Simulator 模擬 3 台實機)
                 │
       MQTT (雙向非同步通訊)
       ├── 上行: nms/devices/{mac}/heartbeat  (每 10s 心跳)
       ├── 上行: nms/devices/{mac}/telemetry  (每 20s CPU/RAM/連線數/PoE/WAN延遲)
       ├── 上行: nms/devices/{mac}/event      (告警事件 / OTA 執行結果)
       └── 下行: nms/devices/{mac}/command    (APPLY_CONFIG / OTA_UPGRADE)
                 ▼
      [ Eclipse Mosquitto Broker ]
                 │
                 ▼
  ┌──────────────────────────────────────────────────────────┐
  │       Spring Boot 3 NMS Backend (REST API + MQTT)        │
  ├────────────────────┬────────────────────┬────────────────┤
  │  Redis             │  MongoDB           │  PostgreSQL    │
  │  • Heartbeat TTL   │  • Telemetry 日誌  │  • 設備主檔     │
  │    自動判定在/離線   │    (30天 TTL 索引) │  • VLAN/SSID   │
  │  • OTA 分散式鎖    │  • 異常告警事件     │    組態紀錄     │
  │    (SET NX EX)     │    (90天 TTL 索引) │  • OTA 任務單   │
  └────────────────────┴────────────────────┴────────────────┘
                 ▲
       RESTful API / Swagger UI (:8080)
       Actuator Health Probes (/actuator/health/liveness & readiness)
```

---

## 2. 關鍵技術選型與設計考量 (Architecture Decisions)

| 元件 | 採用技術 | 為什麼這樣設計？ |
| :--- | :--- | :--- |
| **設備通訊** | **MQTT (Eclipse Paho + Mosquitto)** | 相較於 HTTP Polling，MQTT 具備低頻寬耗損、Keep-Alive 斷線偵測、支援 QoS 1 確保指令至少送達一次，非常適合大規模 AP/Switch/GW 雙向通訊。 |
| **即時狀態與併發控制** | **Redis** | 1. **Heartbeat TTL (`EX 45s`)**：避免數千台設備每秒寫入關聯式資料庫造成 I/O 瓶頸；超過 45 秒沒收到心跳即自動判定 `OFFLINE`。<br>2. **分散式鎖 (`SET NX EX`)**：在 K8s 多副本（Replicas >= 2）環境下，防止同一台設備被重複觸發 OTA 更新。 |
| **監控數據與日誌** | **MongoDB** | AP（連線人數、RSSI）、Switch（PoE 瓦數、Port 狀態）、Gateway（WAN 延遲、VPN Tunnel）的監控指標結構各異，MongoDB 彈性 Schema 最合適，且透過 **TTL Index** 自動清理過期日誌，避免磁碟爆滿。 |
| **核心主檔與交易** | **PostgreSQL** | 儲存設備主檔（MAC Unique Index）、網路組態與 OTA 任務狀態，確保資料強一致性（ACID）。 |
| **容器與邊緣部署** | **Docker / K8s / K3s** | 提供多階段建置 `Dockerfile`（non-root 執行 + JVM 容器記憶體限制）與 K8s/K3s YAML（含 Readiness/Liveness Probe、Graceful Shutdown、HPA）。 |

---

## 3. 專案目錄結構

```text
wifi-nms-demo/
├── backend/                     # Java 17 + Spring Boot 3 後端主程式
│   ├── src/main/java/com/delta/nms/
│   │   ├── controller/          # REST API (Device, OTA) 與全域例外處理
│   │   ├── dto/                 # Request/Response 驗證 DTO
│   │   ├── entity/              # PostgreSQL Entities & MongoDB Documents
│   │   ├── mqtt/                # Paho MQTT 雙向通訊與自動重連重訂閱
│   │   ├── repository/          # JPA & MongoDB Repositories
│   │   └── service/             # 核心業務、Redis 快取與分散式鎖、異常偵測
│   ├── src/test/java/           # JUnit 5 + Mockito 單元測試
│   ├── Dockerfile               # 多階段建置 (Maven -> Temurin 17 JRE)
│   └── pom.xml
├── simulator/                   # 設備模擬器 (模擬 AP / Switch / Gateway)
│   ├── device_simulator.py
│   └── Dockerfile
├── mosquitto/config/            # MQTT Broker 設定
├── k8s/                         # Kubernetes / K3s 部署清單 (ConfigMap/Deployment/Ingress/HPA)
├── docs/DEMO_SCRIPT.md          # 面試現場 5 分鐘 Demo 實戰腳本
├── interview-presentation/      # 面試自我介紹簡報 (.pptx) 與講稿
└── docker-compose.yml           # 一鍵啟動完整環境
```

---

## 4. 快速啟動與測試 (Quick Start & Demo)

### 前置需求
* 安裝 **Docker** 與 **Docker Compose**（不需在本機安裝 Java、Maven 或 Python）。

### Step 1：一鍵啟動全部服務與模擬設備
```bash
cd wifi-nms-demo
docker compose up --build
```
啟動完成後會同時運行：
* **Web 管理介面（React + TypeScript）**：`http://localhost:3000`
* `nms-backend`：`http://localhost:8080`
* **Swagger UI**：`http://localhost:8080/swagger-ui.html`
* **Health Probes**：`http://localhost:8080/actuator/health`
* `nms-device-simulator`：會自動向後端註冊 3 台設備（`AP-Taoyuan3-Lobby-01`、`SW-Taoyuan3-Core-01`、`GW-Taoyuan3-Edge-01`）並開始上報 Heartbeat 與 Telemetry。

### Step 2：透過 API 驗證核心功能

#### (1) 查詢所有設備與即時在線狀態（PostgreSQL + Redis 整合）
```bash
curl -s http://localhost:8080/api/v1/devices | jq .
```
觀察回應中的 `"online": true` 與 `"heartbeatTtlSeconds"`（剩餘 TTL 秒數）。

#### (2) 查詢特定 AP 的即時監控指標（MongoDB）
```bash
curl -s "http://localhost:8080/api/v1/devices/AA:BB:CC:01:00:01/telemetry?limit=5" | jq .
```

#### (3) 下發網路組態（SSID / VLAN / 802.1X RADIUS 設定）
```bash
curl -X POST http://localhost:8080/api/v1/devices/AA:BB:CC:01:00:01/config \
  -H "Content-Type: application/json" \
  -d '{
    "config": {
      "ssid": "Delta-Corp-WiFi7",
      "vlanId": 10,
      "authMode": "WPA3-802.1X",
      "radiusServer": "10.10.1.50"
    }
  }' | jq .
```
此時觀察 `nms-device-simulator` 日誌，會看到 AP 立即透過 MQTT 收到組態並回報 `CONFIG_APPLIED` 事件。

#### (4) 觸發 OTA 韌體升級 & 驗證 Redis 分散式鎖
```bash
curl -X POST http://localhost:8080/api/v1/devices/AA:BB:CC:01:00:01/ota \
  -H "Content-Type: application/json" \
  -d '{
    "targetVersion": "1.3.0",
    "firmwareUrl": "https://fw.delta.local/ap/v1.3.0.bin",
    "sha256Checksum": "e3b0c44298fc1c149afbf4c8996fb924"
  }' | jq .
```
* **測試併發鎖定**：在 5 秒內立刻再執行一次同樣的 `POST /ota`，會收到 `409 Conflict`（`Another OTA task is already in progress`），驗證 Redis 分散式鎖生效。
* **5 秒後**查詢設備與 OTA 紀錄，會看到 `firmwareVersion` 已自動更新為 `1.3.0`，任務狀態變為 `SUCCESS` 且鎖已自動釋放：
```bash
curl -s http://localhost:8080/api/v1/devices/AA:BB:CC:01:00:01/ota | jq .
```

#### (5) 驗證設備斷線自動偵測（Redis TTL 過期）
```bash
docker stop nms-device-simulator
```
等待 45 秒後再次查詢 `GET /api/v1/devices`，所有設備的 `"online"` 會自動轉為 `false`。

#### (6) 手動模擬新納管設備上線、OTA 升級回報與超時回收（Mosquitto CLI）
若透過前端（`http://localhost:3000`）或 API 新增一台未在模擬器中的設備（例如 `AA:BB:CC:01:00:02`），初始狀態會維持 `OFFLINE`（預納管狀態）。可用以下指令手動模擬真實設備行為：

1. **手動發送一筆 MQTT 心跳，讓設備立刻上線（維持 45 秒 TTL）**：
```bash
docker exec nms-mosquitto mosquitto_pub \
  -t "nms/devices/AA:BB:CC:01:00:02/heartbeat" \
  -m '{"ipAddress":"10.10.20.12","firmwareVersion":"1.2.0"}'
```
2. **驗證 OTA 正常回報**：於前端或 API 對該設備觸發 OTA 取得 `taskId`（例如 `OTA-XXXXXXXX`）後，手動模擬設備回報升級成功：
```bash
docker exec nms-mosquitto mosquitto_pub \
  -t "nms/devices/AA:BB:CC:01:00:02/event" \
  -m '{"eventType":"OTA_RESULT","severity":"INFO","message":"Manual OTA complete","details":{"taskId":"換成你的TaskID","status":"SUCCESS","firmwareVersion":"1.3.0","reason":"Manual test"}}'
```
3. **驗證 OTA 超時自動回收（Timeout Watchdog）**：若觸發 OTA 後不回報 `OTA_RESULT`，超過 120 秒後後端 `@Scheduled` Watchdog 會自動將任務標記為 `FAILED`、釋放 Redis 鎖，並在 MongoDB `device_events` 寫入 `CRITICAL / OTA_TIMEOUT` 告警。

#### (7) 使用 MongoDB Compass 檢視時序資料與 TTL Index
Docker Compose 已將 MongoDB 對應至本機 `27018` Port（避免與本機既有 `27017` 衝突），在 MongoDB Compass 輸入以下位址即可連線：
```text
mongodb://localhost:27018/wifinms_logs
```

### Step 3：執行單元測試（使用 Docker Maven 容器）
```bash
docker run --rm -v "$(pwd)/backend":/app -w /app maven:3.9-eclipse-temurin-17 mvn test
```

---

## 5. Kubernetes / K3s / Minikube 部署與維運排查

本專案於 `k8s/` 目錄提供完整的雲原生部署清單（包含 PostgreSQL、MongoDB、Redis、Mosquitto 及 Backend Deployment / HPA / Ingress，相容 K8s、Minikube 與輕量邊緣環境 **K3s**）：

```bash
# 1. 若使用本機 Minikube，請先確認叢集已啟動
minikube start

# 2. 建置後端映像檔（使用 Docker cache 會很快完成）
docker build -t wifi-nms-backend:1.0.0 ./backend

# 3. 將本機映像檔載入 Minikube（若使用 K3s 或遠端 Registry 則略過此步）
minikube image load wifi-nms-backend:1.0.0

# 4. 套用全部 K8s 清單（含基礎設施與後端服務）
kubectl apply -f k8s/

# 5. 本機轉發 Port 驗證 Swagger UI / API (http://localhost:8081/swagger-ui.html)
kubectl port-forward -n wifi-nms svc/nms-backend-svc 8081:80
```

### Production Debugging 常用排查指令
```bash
# 1. 檢查 Pod 狀態與資源配置
kubectl get pods -n wifi-nms -o wide

# 2. 排查 CrashLoopBackOff / OOMKilled / Probe 失敗事件
kubectl describe pod -n wifi-nms -l app=nms-backend

# 3. 查看當機前的錯誤日誌
kubectl logs -n wifi-nms -l app=nms-backend --previous --tail=100

# 4. 檢查 HPA 自動擴縮容狀態
kubectl get hpa -n wifi-nms
```
