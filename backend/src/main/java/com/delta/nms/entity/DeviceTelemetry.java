package com.delta.nms.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "device_telemetry")
@CompoundIndex(name = "mac_timestamp_idx", def = "{'macAddress': 1, 'timestamp': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceTelemetry {

    @Id
    private String id;

    private String macAddress;

    private String deviceType;

    private Double cpuUsage;

    private Double memoryUsage;

    private Double temperatureC;

    /**
     * Flexible metrics per device type:
     * - AP: connectedClients, channel2g, channel5g, txBytes, rxBytes
     * - SWITCH: activePorts, poePowerWatts, vlanCount
     * - GATEWAY: wanLatencyMs, activeSessions, vpnTunnels
     */
    private Map<String, Object> metrics;

    /**
     * MongoDB TTL index: auto-expire telemetry documents after 30 days (2,592,000 seconds)
     */
    @Indexed(expireAfterSeconds = 2592000)
    private Instant timestamp;
}
