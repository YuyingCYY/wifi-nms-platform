package com.delta.nms.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class DeviceConfigRequest {

    /**
     * Flexible key-value configuration, e.g.:
     * AP:      { "ssid": "Delta-Corp-5G", "vlanId": 10, "authMode": "WPA3-802.1X", "radiusServer": "10.10.1.50" }
     * SWITCH:  { "trunkPorts": ["eth0", "eth1"], "allowedVlans": [10, 20, 30], "poeEnabled": true }
     * GATEWAY: { "dhcpRange": "192.168.10.100-200", "dnsServers": ["8.8.8.8", "1.1.1.1"] }
     */
    @NotEmpty(message = "Configuration payload must not be empty")
    private Map<String, Object> config;
}
