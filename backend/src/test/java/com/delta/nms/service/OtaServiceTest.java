package com.delta.nms.service;

import com.delta.nms.dto.OtaUpgradeRequest;
import com.delta.nms.entity.Device;
import com.delta.nms.entity.DeviceEvent;
import com.delta.nms.entity.DeviceType;
import com.delta.nms.entity.OtaStatus;
import com.delta.nms.entity.OtaTask;
import com.delta.nms.mqtt.MqttCommandPublisher;
import com.delta.nms.repository.DeviceEventRepository;
import com.delta.nms.repository.DeviceRepository;
import com.delta.nms.repository.OtaTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtaServiceTest {

    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private OtaTaskRepository otaTaskRepository;
    @Mock
    private DeviceEventRepository eventRepository;
    @Mock
    private DeviceStatusCacheService statusCacheService;
    @Mock
    private MqttCommandPublisher mqttCommandPublisher;

    @InjectMocks
    private OtaService otaService;

    private Device sampleAp;
    private OtaUpgradeRequest sampleReq;

    @BeforeEach
    void setUp() {
        sampleAp = Device.builder()
                .id(1L)
                .macAddress("AA:BB:CC:01:00:01")
                .name("AP-Lobby-01")
                .type(DeviceType.AP)
                .firmwareVersion("1.2.0")
                .build();

        sampleReq = new OtaUpgradeRequest();
        sampleReq.setTargetVersion("1.3.0");
        sampleReq.setFirmwareUrl("https://fw.delta.local/ap/v1.3.0.bin");
        sampleReq.setSha256Checksum("abc123def456");
    }

    @Test
    void triggerOtaUpgrade_shouldSucceedWhenOnlineAndLockAcquired() {
        when(deviceRepository.findByMacAddress("AA:BB:CC:01:00:01")).thenReturn(Optional.of(sampleAp));
        when(statusCacheService.isOnline("AA:BB:CC:01:00:01")).thenReturn(true);
        when(statusCacheService.tryAcquireOtaLock(eq("AA:BB:CC:01:00:01"), anyString())).thenReturn(true);
        when(otaTaskRepository.save(any(OtaTask.class))).thenAnswer(inv -> inv.getArgument(0));

        OtaTask result = otaService.triggerOtaUpgrade("aa:bb:cc:01:00:01", sampleReq);

        assertNotNull(result);
        assertEquals(OtaStatus.IN_PROGRESS, result.getStatus());
        assertEquals("1.3.0", result.getTargetVersion());
        verify(mqttCommandPublisher, times(1))
                .publishCommand(eq("AA:BB:CC:01:00:01"), eq("OTA_UPGRADE"), anyMap());
    }

    @Test
    void triggerOtaUpgrade_shouldFailWhenDeviceIsOffline() {
        when(deviceRepository.findByMacAddress("AA:BB:CC:01:00:01")).thenReturn(Optional.of(sampleAp));
        when(statusCacheService.isOnline("AA:BB:CC:01:00:01")).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> otaService.triggerOtaUpgrade("AA:BB:CC:01:00:01", sampleReq));

        assertTrue(ex.getMessage().contains("OFFLINE"));
        verify(mqttCommandPublisher, never()).publishCommand(any(), any(), any());
    }

    @Test
    void triggerOtaUpgrade_shouldFailWhenConcurrentOtaLockExists() {
        when(deviceRepository.findByMacAddress("AA:BB:CC:01:00:01")).thenReturn(Optional.of(sampleAp));
        when(statusCacheService.isOnline("AA:BB:CC:01:00:01")).thenReturn(true);
        when(statusCacheService.tryAcquireOtaLock(eq("AA:BB:CC:01:00:01"), anyString())).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> otaService.triggerOtaUpgrade("AA:BB:CC:01:00:01", sampleReq));

        assertTrue(ex.getMessage().contains("already in progress"));
        verify(otaTaskRepository, never()).save(any());
    }

    @Test
    void triggerOtaUpgrade_shouldReleaseLockIfMqttPublishFails() {
        when(deviceRepository.findByMacAddress("AA:BB:CC:01:00:01")).thenReturn(Optional.of(sampleAp));
        when(statusCacheService.isOnline("AA:BB:CC:01:00:01")).thenReturn(true);
        when(statusCacheService.tryAcquireOtaLock(eq("AA:BB:CC:01:00:01"), anyString())).thenReturn(true);
        when(otaTaskRepository.save(any(OtaTask.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("MQTT disconnected"))
                .when(mqttCommandPublisher).publishCommand(any(), any(), any());

        assertThrows(IllegalStateException.class,
                () -> otaService.triggerOtaUpgrade("AA:BB:CC:01:00:01", sampleReq));

        verify(statusCacheService, times(1)).releaseOtaLock("AA:BB:CC:01:00:01");
    }

    @Test
    void reconcileTimedOutOtaTasks_shouldMarkFailedAndLogCriticalEvent() {
        OtaTask stale = OtaTask.builder()
                .taskId("OTA-STALE001")
                .macAddress("AA:BB:CC:01:00:02")
                .targetVersion("1.3.0")
                .status(OtaStatus.IN_PROGRESS)
                .createdAt(Instant.now().minusSeconds(300))
                .build();

        when(otaTaskRepository.findByStatusAndCreatedAtBefore(eq(OtaStatus.IN_PROGRESS), any(Instant.class)))
                .thenReturn(List.of(stale));

        otaService.reconcileTimedOutOtaTasks();

        assertEquals(OtaStatus.FAILED, stale.getStatus());
        assertNotNull(stale.getCompletedAt());
        assertTrue(stale.getMessage().contains("timed out"));
        verify(otaTaskRepository, times(1)).save(stale);
        verify(statusCacheService, times(1)).releaseOtaLock("AA:BB:CC:01:00:02");
        verify(eventRepository, times(1)).save(any(DeviceEvent.class));
    }
}
