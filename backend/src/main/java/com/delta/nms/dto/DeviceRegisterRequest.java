package com.delta.nms.dto;

import com.delta.nms.entity.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DeviceRegisterRequest {

    @NotBlank(message = "MAC address is required")
    @Pattern(regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$", message = "Invalid MAC address format (e.g. AA:BB:CC:01:00:01)")
    private String macAddress;

    @NotBlank(message = "Device name is required")
    private String name;

    @NotNull(message = "Device type is required (AP, SWITCH, GATEWAY)")
    private DeviceType type;

    @NotBlank(message = "Model is required")
    private String model;

    @NotBlank(message = "Site name is required")
    private String siteName;

    private String ipAddress;

    @NotBlank(message = "Firmware version is required")
    private String firmwareVersion;
}
