package com.delta.nms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusCacheService {

    private static final String ONLINE_KEY_PREFIX = "nms:device:online:";
    private static final String OTA_LOCK_PREFIX = "nms:lock:ota:";

    private final StringRedisTemplate redisTemplate;

    @Value("${nms.device.heartbeat-ttl-seconds:45}")
    private long heartbeatTtlSeconds;

    @Value("${nms.device.ota-lock-ttl-seconds:120}")
    private long otaLockTtlSeconds;

    public void markOnline(String macAddress, String ipAddress) {
        String key = ONLINE_KEY_PREFIX + macAddress.toUpperCase();
        String value = ipAddress != null ? ipAddress : "ONLINE";
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(heartbeatTtlSeconds));
    }

    public boolean isOnline(String macAddress) {
        String key = ONLINE_KEY_PREFIX + macAddress.toUpperCase();
        return redisTemplate.hasKey(key);
    }

    public Long getHeartbeatTtl(String macAddress) {
        String key = ONLINE_KEY_PREFIX + macAddress.toUpperCase();
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl > 0 ? ttl : 0L;
    }

    /**
     * Distributed lock using Redis SET NX EX to prevent concurrent OTA upgrades on the same device.
     */
    public boolean tryAcquireOtaLock(String macAddress, String taskId) {
        String key = OTA_LOCK_PREFIX + macAddress.toUpperCase();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, taskId, Duration.ofSeconds(otaLockTtlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseOtaLock(String macAddress) {
        String key = OTA_LOCK_PREFIX + macAddress.toUpperCase();
        redisTemplate.delete(key);
        log.info("Released OTA distributed lock for device {}", macAddress);
    }
}
