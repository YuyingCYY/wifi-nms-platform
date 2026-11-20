package com.delta.nms.controller;

import com.delta.nms.dto.DeviceConfigRequest;
import com.delta.nms.dto.DeviceRegisterRequest;
import com.delta.nms.dto.DeviceResponse;
import com.delta.nms.entity.DeviceEvent;
import com.delta.nms.entity.DeviceTelemetry;
import com.delta.nms.entity.DeviceType;
import com.delta.nms.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Management", description = "AP / Switch / Gateway lifecycle, configuration, and monitoring")
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new networking device (AP / Switch / Gateway)")
    public DeviceResponse registerDevice(@Valid @RequestBody DeviceRegisterRequest request) {
        return deviceService.registerDevice(request);
    }

    @GetMapping
    @Operation(summary = "List devices with real-time online status from Redis")
    public List<DeviceResponse> listDevices(
            @RequestParam(required = false) String siteName,
            @RequestParam(required = false) DeviceType type) {
        return deviceService.listDevices(siteName, type);
    }

    @GetMapping("/{macAddress}")
    @Operation(summary = "Get single device details and heartbeat TTL")
    public DeviceResponse getDevice(@PathVariable String macAddress) {
        return deviceService.getDevice(macAddress);
    }

    @PostMapping("/{macAddress}/config")
    @Operation(summary = "Update and push configuration (VLAN / SSID / 802.1X) to device via MQTT")
    public DeviceResponse applyConfig(
            @PathVariable String macAddress,
            @Valid @RequestBody DeviceConfigRequest request) {
        return deviceService.applyConfig(macAddress, request);
    }

    @GetMapping("/{macAddress}/telemetry")
    @Operation(summary = "Query recent device telemetry metrics from MongoDB")
    public List<DeviceTelemetry> getTelemetry(
            @PathVariable String macAddress,
            @RequestParam(defaultValue = "20") int limit) {
        return deviceService.getRecentTelemetry(macAddress, limit);
    }

    @GetMapping("/{macAddress}/events")
    @Operation(summary = "Query recent device alerts/events from MongoDB")
    public List<DeviceEvent> getDeviceEvents(
            @PathVariable String macAddress,
            @RequestParam(defaultValue = "20") int limit) {
        return deviceService.getRecentEvents(macAddress, limit);
    }

    @GetMapping("/events/recent")
    @Operation(summary = "Query system-wide recent alerts/events from MongoDB")
    public List<DeviceEvent> getAllRecentEvents(@RequestParam(defaultValue = "30") int limit) {
        return deviceService.getRecentEvents(null, limit);
    }
}
