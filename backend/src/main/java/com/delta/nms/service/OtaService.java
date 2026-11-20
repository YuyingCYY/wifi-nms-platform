package com.delta.nms.service;

import com.delta.nms.dto.OtaUpgradeRequest;
import com.delta.nms.entity.Device;
import com.delta.nms.entity.DeviceEvent;
import com.delta.nms.entity.OtaStatus;
import com.delta.nms.entity.OtaTask;
import com.delta.nms.mqtt.MqttCommandPublisher;
import com.delta.nms.repository.DeviceEventRepository;
import com.delta.nms.repository.DeviceRepository;
import com.delta.nms.repository.OtaTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtaService {

    private final DeviceRepository deviceRepository;
    private final OtaTaskRepository otaTaskRepository;
    private final DeviceEventRepository eventRepository;
    private final DeviceStatusCacheService statusCacheService;
    private final MqttCommandPublisher mqttCommandPublisher;

    @Value("${nms.device.ota-lock-ttl-seconds:120}")
    private long otaTimeoutSeconds = 120L;

    @Transactional
    public OtaTask triggerOtaUpgrade(String macAddress, OtaUpgradeRequest request) {
        String normalizedMac = macAddress.toUpperCase();
        Device device = deviceRepository.findByMacAddress(normalizedMac)
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + normalizedMac));

        // 1. Check real-time online status from Redis
        if (!statusCacheService.isOnline(normalizedMac)) {
            throw new IllegalStateException("Cannot trigger OTA: device " + normalizedMac + " is currently OFFLINE");
        }

        // 2. Acquire Redis distributed lock to prevent concurrent OTA upgrades on the same device
        String taskId = "OTA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        boolean lockAcquired = statusCacheService.tryAcquireOtaLock(normalizedMac, taskId);
        if (!lockAcquired) {
            throw new IllegalStateException("Another OTA task is already in progress for device " + normalizedMac);
        }

        try {
            // 3. Persist task in PostgreSQL
            OtaTask task = OtaTask.builder()
                    .taskId(taskId)
                    .macAddress(normalizedMac)
                    .targetVersion(request.getTargetVersion())
                    .firmwareUrl(request.getFirmwareUrl())
                    .sha256Checksum(request.getSha256Checksum())
                    .status(OtaStatus.IN_PROGRESS)
                    .message("Command dispatched via MQTT")
                    .build();
            OtaTask saved = otaTaskRepository.save(task);

            // 4. Dispatch OTA command via MQTT
            Map<String, Object> payload = Map.of(
                    "taskId", taskId,
                    "currentVersion", device.getFirmwareVersion(),
                    "targetVersion", request.getTargetVersion(),
                    "firmwareUrl", request.getFirmwareUrl(),
                    "sha256Checksum", request.getSha256Checksum() != null ? request.getSha256Checksum() : ""
            );
            mqttCommandPublisher.publishCommand(normalizedMac, "OTA_UPGRADE", payload);

            log.info("Triggered OTA task {} for device {} -> target version {}",
                    taskId, normalizedMac, request.getTargetVersion());
            return saved;
        } catch (RuntimeException e) {
            // Rollback distributed lock if publishing fails
            statusCacheService.releaseOtaLock(normalizedMac);
            throw e;
        }
    }

    public List<OtaTask> getOtaHistory(String macAddress) {
        return otaTaskRepository.findByMacAddressOrderByCreatedAtDesc(macAddress.toUpperCase());
    }

    /**
     * Scheduled watchdog: automatically marks stale IN_PROGRESS OTA tasks as FAILED
     * when a device disconnects or fails to report OTA_RESULT before timeout.
     */
    @Scheduled(fixedDelayString = "${nms.device.ota-watchdog-interval-ms:30000}")
    @Transactional
    public void reconcileTimedOutOtaTasks() {
        Instant cutoff = Instant.now().minusSeconds(otaTimeoutSeconds);
        List<OtaTask> staleTasks = otaTaskRepository.findByStatusAndCreatedAtBefore(OtaStatus.IN_PROGRESS, cutoff);
        if (staleTasks.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (OtaTask task : staleTasks) {
            task.setStatus(OtaStatus.FAILED);
            task.setMessage("OTA timed out: no response from device within " + otaTimeoutSeconds + "s");
            task.setCompletedAt(now);
            otaTaskRepository.save(task);

            statusCacheService.releaseOtaLock(task.getMacAddress());

            DeviceEvent alert = DeviceEvent.builder()
                    .macAddress(task.getMacAddress())
                    .severity("CRITICAL")
                    .eventType("OTA_TIMEOUT")
                    .message(String.format("OTA task %s (-> v%s) timed out after %ds",
                            task.getTaskId(), task.getTargetVersion(), otaTimeoutSeconds))
                    .details(Map.of(
                            "taskId", task.getTaskId(),
                            "targetVersion", task.getTargetVersion(),
                            "timeoutSeconds", otaTimeoutSeconds
                    ))
                    .timestamp(now)
                    .build();
            eventRepository.save(alert);

            log.warn("Marked stale OTA task {} for device {} as FAILED (timeout)",
                    task.getTaskId(), task.getMacAddress());
        }
    }
}
