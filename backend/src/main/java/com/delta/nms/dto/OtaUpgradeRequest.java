package com.delta.nms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtaUpgradeRequest {

    @NotBlank(message = "Target firmware version is required")
    private String targetVersion;

    @NotBlank(message = "Firmware download URL is required")
    private String firmwareUrl;

    private String sha256Checksum;
}
