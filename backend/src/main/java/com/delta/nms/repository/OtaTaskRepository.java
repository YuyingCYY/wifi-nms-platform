package com.delta.nms.repository;

import com.delta.nms.entity.OtaStatus;
import com.delta.nms.entity.OtaTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtaTaskRepository extends JpaRepository<OtaTask, Long> {
    Optional<OtaTask> findByTaskId(String taskId);
    List<OtaTask> findByMacAddressOrderByCreatedAtDesc(String macAddress);
    List<OtaTask> findByStatusAndCreatedAtBefore(OtaStatus status, Instant cutoff);
}
