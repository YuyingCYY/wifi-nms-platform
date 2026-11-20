package com.delta.nms.service;

import com.delta.nms.dto.DeviceConfigRequest;
import com.delta.nms.dto.DeviceRegisterRequest;
import com.delta.nms.dto.DeviceResponse;
import com.delta.nms.entity.Device;
import com.delta.nms.entity.DeviceEvent;
import com.delta.nms.entity.DeviceTelemetry;
import com.delta.nms.entity.DeviceType;
import com.delta.nms.mqtt.MqttCommandPublisher;
import com.delta.nms.repository.DeviceEventRepository;
import com.delta.nms.repository.DeviceRepository;
import com.delta.nms.repository.DeviceTelemetryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceTelemetryRepository telemetryRepository;
    private final DeviceEventRepository eventRepository;
    private final DeviceStatusCacheService statusCacheService;
    private final MqttCommandPublisher mqttCommandPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeviceResponse registerDevice(DeviceRegisterRequest req) {
        String normalizedMac = req.getMacAddress().toUpperCase();
        if (deviceRepository.existsByMacAddress(normalizedMac)) {
            throw new IllegalArgumentException("Device already registered with MAC: " + normalizedMac);
        }

        Device device = Device.builder()
                .macAddress(normalizedMac)
                .name(req.getName())
                .type(req.getType())
                .model(req.getModel())
                .siteName(req.getSiteName())
                .ipAddress(req.getIpAddress())
                .firmwareVersion(req.getFirmwareVersion())
                .configJson("{}")
                .build();

        Device saved = deviceRepository.save(device);
        log.info("Registered new {} device: {} ({})", saved.getType(), saved.getName(), saved.getMacAddress());
        return toResponse(saved);
    }

    public List<DeviceResponse> listDevices(String siteName, DeviceType type) {
        List<Device> devices;
        if (siteName != null && !siteName.isBlank()) {
            devices = deviceRepository.findBySiteName(siteName);
        } else if (type != null) {
            devices = deviceRepository.findByType(type);
        } else {
            devices = deviceRepository.findAll();
        }
        return devices.stream().map(this::toResponse).toList();
    }

    public DeviceResponse getDevice(String macAddress) {
        Device device = findOrThrow(macAddress);
        return toResponse(device);
    }

    @Transactional
    public DeviceResponse applyConfig(String macAddress, DeviceConfigRequest req) {
        Device device = findOrThrow(macAddress);
        try {
            String configJson = objectMapper.writeValueAsString(req.getConfig());
            device.setConfigJson(configJson);
            deviceRepository.save(device);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid configuration JSON", e);
        }

        // Push configuration down to device via MQTT
        mqttCommandPublisher.publishCommand(device.getMacAddress(), "APPLY_CONFIG", req.getConfig());
        log.info("Applied and pushed config to device {}", device.getMacAddress());
        return toResponse(device);
    }

    public List<DeviceTelemetry> getRecentTelemetry(String macAddress, int limit) {
        findOrThrow(macAddress);
        return telemetryRepository.findByMacAddressOrderByTimestampDesc(
                macAddress.toUpperCase(), PageRequest.of(0, Math.min(limit, 100)));
    }

    public List<DeviceEvent> getRecentEvents(String macAddress, int limit) {
        if (macAddress == null || macAddress.isBlank()) {
            return eventRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, Math.min(limit, 100)));
        }
        findOrThrow(macAddress);
        return eventRepository.findByMacAddressOrderByTimestampDesc(
                macAddress.toUpperCase(), PageRequest.of(0, Math.min(limit, 100)));
    }

    private Device findOrThrow(String macAddress) {
        return deviceRepository.findByMacAddress(macAddress.toUpperCase())
                .orElseThrow(() -> new NoSuchElementException("Device not found: " + macAddress));
    }

    private DeviceResponse toResponse(Device device) {
        boolean online = statusCacheService.isOnline(device.getMacAddress());
        Long ttl = online ? statusCacheService.getHeartbeatTtl(device.getMacAddress()) : 0L;
        return DeviceResponse.builder()
                .id(device.getId())
                .macAddress(device.getMacAddress())
                .name(device.getName())
                .type(device.getType())
                .model(device.getModel())
                .siteName(device.getSiteName())
                .ipAddress(device.getIpAddress())
                .firmwareVersion(device.getFirmwareVersion())
                .configJson(device.getConfigJson())
                .online(online)
                .heartbeatTtlSeconds(ttl)
                .lastSeenAt(device.getLastSeenAt())
                .build();
    }
}
