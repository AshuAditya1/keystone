package com.meridian.keystone.repository;

import com.meridian.keystone.domain.WorkOrderStatusHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * The append-only audit trail. Nothing here updates or deletes rows — history
 * is written once and read forever.
 */
public interface WorkOrderStatusHistoryRepository
        extends JpaRepository<WorkOrderStatusHistory, Long> {

    @EntityGraph(attributePaths = {"changedBy"})
    List<WorkOrderStatusHistory> findByWorkOrderIdOrderByCreatedAtAsc(Long workOrderId);

    /** Global activity feed — only ever shown to roles that can see everything. */
    @EntityGraph(attributePaths = {"changedBy", "workOrder"})
    List<WorkOrderStatusHistory> findTop15ByOrderByCreatedAtDesc();

    /**
     * Activity feed restricted to a known-visible set of work orders, so a
     * technician's or customer's dashboard cannot leak other people's jobs.
     */
    @EntityGraph(attributePaths = {"changedBy", "workOrder"})
    List<WorkOrderStatusHistory> findByWorkOrderIdInOrderByCreatedAtDesc(
            Collection<Long> workOrderIds, Pageable pageable);
}
