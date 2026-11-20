package com.delta.nms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "devices", indexes = {
        @Index(name = "idx_device_mac", columnList = "macAddress", unique = true),
        @Index(name = "idx_device_site", columnList = "siteName")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String macAddress;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceType type;

    @Column(nullable = false, length = 64)
    private String model;

    @Column(nullable = false, length = 64)
    private String siteName;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 32)
    private String firmwareVersion;

    /**
     * JSON string storing device-specific config (e.g. SSID/VLAN/802.1X for AP, VLAN trunk for Switch)
     */
    @Column(columnDefinition = "TEXT")
    private String configJson;

    private Instant lastSeenAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
