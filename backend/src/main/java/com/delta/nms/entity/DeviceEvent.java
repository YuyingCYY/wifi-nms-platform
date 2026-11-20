package com.delta.nms.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "device_events")
@CompoundIndex(name = "event_mac_time_idx", def = "{'macAddress': 1, 'timestamp': -1}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceEvent {

    @Id
    private String id;

    private String macAddress;

    /**
     * INFO, WARNING, CRITICAL
     */
    private String severity;

    /**
     * e.g. HIGH_CPU, PORT_DOWN, RADIUS_AUTH_FAIL, OTA_RESULT, CONFIG_APPLIED
     */
    private String eventType;

    private String message;

    private Map<String, Object> details;

    /**
     * MongoDB TTL index: auto-expire event logs after 90 days (7,776,000 seconds)
     */
    @Indexed(expireAfterSeconds = 7776000)
    private Instant timestamp;
}
