package com.delta.nms.service;

import com.delta.nms.entity.Device;
import com.delta.nms.entity.DeviceEvent;
import com.delta.nms.entity.DeviceTelemetry;
import com.delta.nms.entity.OtaStatus;
import com.delta.nms.repository.DeviceEventRepository;
import com.delta.nms.repository.DeviceRepository;
import com.delta.nms.repository.DeviceTelemetryRepository;
import com.delta.nms.repository.OtaTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMessageProcessor {

    private static final double CPU_ALERT_THRESHOLD = 85.0;
    private static final double TEMP_ALERT_THRESHOLD = 75.0;

    private final DeviceRepository deviceRepository;
    private final OtaTaskRepository otaTaskRepository;
    private final DeviceTelemetryRepository telemetryRepository;
    private final DeviceEventRepository eventRepository;
    private final DeviceStatusCacheService statusCacheService;
    private final ObjectMapper objectMapper;

    public void handleIncomingMessage(String topic, String payload) {
        // Expected topic format: nms/devices/{mac}/{channel}
        String[] parts = topic.split("/");
        if (parts.length != 4) {
            log.warn("Ignoring unexpected MQTT topic format: {}", topic);
            return;
        }

        String macAddress = parts[2].toUpperCase();
        String channel = parts[3];

        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            switch (channel) {
                case "heartbeat" -> processHeartbeat(macAddress, data);
                case "telemetry" -> processTelemetry(macAddress, data);
                case "event" -> processEvent(macAddress, data);
                default -> log.warn("Unknown channel '{}' from device {}", channel, macAddress);
            }
        } catch (Exception e) {
            log.error("Failed to process MQTT payload from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    private void processHeartbeat(String macAddress, Map<String, Object> data) {
        String ipAddress = data.get("ipAddress") != null ? data.get("ipAddress").toString() : null;
        statusCacheService.markOnline(macAddress, ipAddress);

        deviceRepository.findByMacAddress(macAddress).ifPresent(device -> {
            device.setLastSeenAt(Instant.now());
            if (ipAddress != null && !ipAddress.isBlank()) {
                device.setIpAddress(ipAddress);
            }
            deviceRepository.save(device);
        });
        log.debug("Heartbeat received from {} (IP={})", macAddress, ipAddress);
    }

    @SuppressWarnings("unchecked")
    private void processTelemetry(String macAddress, Map<String, Object> data) {
        // Refresh online state on telemetry as well
        statusCacheService.markOnline(macAddress, null);

        Double cpu = toDouble(data.get("cpuUsage"));
        Double mem = toDouble(data.get("memoryUsage"));
        Double temp = toDouble(data.get("temperatureC"));
        String deviceType = data.get("deviceType") != null ? data.get("deviceType").toString() : "UNKNOWN";

        Map<String, Object> metrics = data.get("metrics") instanceof Map<?, ?> m
                ? new HashMap<>((Map<String, Object>) m)
                : Map.of();

        DeviceTelemetry telemetry = DeviceTelemetry.builder()
                .macAddress(macAddress)
                .deviceType(deviceType)
                .cpuUsage(cpu)
                .memoryUsage(mem)
                .temperatureC(temp)
                .metrics(metrics)
                .timestamp(Instant.now())
                .build();
        telemetryRepository.save(telemetry);

        // Threshold-based anomaly detection
        if (cpu != null && cpu > CPU_ALERT_THRESHOLD) {
            saveAlertEvent(macAddress, "WARNING", "HIGH_CPU",
                    String.format("Device CPU usage reached %.1f%% (threshold %.1f%%)", cpu, CPU_ALERT_THRESHOLD),
                    Map.of("cpuUsage", cpu));
        }
        if (temp != null && temp > TEMP_ALERT_THRESHOLD) {
            saveAlertEvent(macAddress, "CRITICAL", "HIGH_TEMPERATURE",
                    String.format("Device temperature reached %.1fC (threshold %.1fC)", temp, TEMP_ALERT_THRESHOLD),
                    Map.of("temperatureC", temp));
        }
    }

    @SuppressWarnings("unchecked")
    private void processEvent(String macAddress, Map<String, Object> data) {
        String eventType = String.valueOf(data.getOrDefault("eventType", "GENERIC_EVENT"));
        String severity = String.valueOf(data.getOrDefault("severity", "INFO"));
        String message = String.valueOf(data.getOrDefault("message", ""));
        Map<String, Object> details = data.get("details") instanceof Map<?, ?> m
                ? new HashMap<>((Map<String, Object>) m)
                : Map.of();

        saveAlertEvent(macAddress, severity, eventType, message, details);

        // Handle OTA result callback from device
        if ("OTA_RESULT".equalsIgnoreCase(eventType)) {
            handleOtaResult(macAddress, details);
        }
    }

    private void handleOtaResult(String macAddress, Map<String, Object> details) {
        String taskId = details.get("taskId") != null ? details.get("taskId").toString() : null;
        String result = details.get("status") != null ? details.get("status").toString() : "FAILED";
        String newVersion = details.get("firmwareVersion") != null ? details.get("firmwareVersion").toString() : null;

        if (taskId != null) {
            otaTaskRepository.findByTaskId(taskId).ifPresent(task -> {
                boolean success = "SUCCESS".equalsIgnoreCase(result);
                task.setStatus(success ? OtaStatus.SUCCESS : OtaStatus.FAILED);
                task.setMessage(String.valueOf(details.getOrDefault("reason", result)));
                task.setCompletedAt(Instant.now());
                otaTaskRepository.save(task);

                if (success && newVersion != null) {
                    deviceRepository.findByMacAddress(macAddress).ifPresent(device -> {
                        device.setFirmwareVersion(newVersion);
                        deviceRepository.save(device);
                    });
                }
                log.info("OTA task {} for device {} finished with status {}", taskId, macAddress, task.getStatus());
            });
        }
        // Always release distributed lock once device reports completion
        statusCacheService.releaseOtaLock(macAddress);
    }

    private void saveAlertEvent(String macAddress, String severity, String eventType,
                                String message, Map<String, Object> details) {
        DeviceEvent event = DeviceEvent.builder()
                .macAddress(macAddress)
                .severity(severity)
                .eventType(eventType)
                .message(message)
                .details(details)
                .timestamp(Instant.now())
                .build();
        eventRepository.save(event);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return null;
    }
}
