package com.delta.nms.dto;

import com.delta.nms.entity.DeviceType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class DeviceResponse {
    private Long id;
    private String macAddress;
    private String name;
    private DeviceType type;
    private String model;
    private String siteName;
    private String ipAddress;
    private String firmwareVersion;
    private String configJson;
    private boolean online;
    private Long heartbeatTtlSeconds;
    private Instant lastSeenAt;
}
