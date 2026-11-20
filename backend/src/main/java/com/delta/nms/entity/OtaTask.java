package com.delta.nms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "ota_tasks", indexes = {
        @Index(name = "idx_ota_mac", columnList = "macAddress")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtaTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(nullable = false, length = 32)
    private String macAddress;

    @Column(nullable = false, length = 32)
    private String targetVersion;

    @Column(nullable = false, length = 256)
    private String firmwareUrl;

    @Column(length = 128)
    private String sha256Checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OtaStatus status;

    @Column(length = 256)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant completedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
