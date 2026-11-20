package com.delta.nms.repository;

import com.delta.nms.entity.DeviceEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DeviceEventRepository extends MongoRepository<DeviceEvent, String> {
    List<DeviceEvent> findByMacAddressOrderByTimestampDesc(String macAddress, Pageable pageable);
    List<DeviceEvent> findAllByOrderByTimestampDesc(Pageable pageable);
}
