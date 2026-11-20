package com.delta.nms.repository;

import com.delta.nms.entity.Device;
import com.delta.nms.entity.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByMacAddress(String macAddress);
    boolean existsByMacAddress(String macAddress);
    List<Device> findBySiteName(String siteName);
    List<Device> findByType(DeviceType type);
}
