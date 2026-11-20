package com.delta.nms.repository;

import com.delta.nms.entity.DeviceTelemetry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeviceTelemetryRepository extends MongoRepository<DeviceTelemetry, String> {
    List<DeviceTelemetry> findByMacAddressOrderByTimestampDesc(String macAddress, Pageable pageable);
}
