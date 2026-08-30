package com.meridian.keystone.repository;

import com.meridian.keystone.domain.PartUsage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartUsageRepository extends JpaRepository<PartUsage, Long> {

    @EntityGraph(attributePaths = {"part", "loggedBy"})
    List<PartUsage> findByWorkOrderIdOrderByCreatedAtAsc(Long workOrderId);

    @EntityGraph(attributePaths = {"part"})
    Optional<PartUsage> findByIdAndWorkOrderId(Long id, Long workOrderId);

    boolean existsByPartId(Long partId);
}
