package com.meridian.keystone.repository;

import com.meridian.keystone.domain.TimeLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeLogRepository extends JpaRepository<TimeLog, Long> {

    @EntityGraph(attributePaths = {"technician"})
    List<TimeLog> findByWorkOrderIdOrderByCreatedAtAsc(Long workOrderId);

    boolean existsByTechnicianId(Long technicianId);
}
