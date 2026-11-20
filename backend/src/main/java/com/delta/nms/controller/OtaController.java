package com.delta.nms.controller;

import com.delta.nms.dto.OtaUpgradeRequest;
import com.delta.nms.entity.OtaTask;
import com.delta.nms.service.OtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/devices/{macAddress}/ota")
@RequiredArgsConstructor
@Tag(name = "OTA Firmware Management", description = "Trigger and track OTA upgrades with Redis distributed lock")
public class OtaController {

    private final OtaService otaService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger OTA firmware upgrade for an online device")
    public OtaTask triggerOta(
            @PathVariable String macAddress,
            @Valid @RequestBody OtaUpgradeRequest request) {
        return otaService.triggerOtaUpgrade(macAddress, request);
    }

    @GetMapping
    @Operation(summary = "List OTA upgrade history for a device")
    public List<OtaTask> getOtaHistory(@PathVariable String macAddress) {
        return otaService.getOtaHistory(macAddress);
    }
}
