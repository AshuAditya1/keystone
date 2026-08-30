package com.meridian.keystone.repository;

import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository
        extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    /**
     * Paged search. customer/site/assignee are single-valued associations, so
     * fetching them alongside a paged query is safe (no in-memory paging) and
     * keeps the list endpoint at one query instead of N+1.
     */
    @Override
    @EntityGraph(attributePaths = {"customer", "site", "assignee"})
    Page<WorkOrder> findAll(Specification<WorkOrder> spec, Pageable pageable);

    /** Unpaged scoped fetch — used by the Kanban board and the dashboard. */
    @Override
    @EntityGraph(attributePaths = {"customer", "site", "assignee"})
    List<WorkOrder> findAll(Specification<WorkOrder> spec, Sort sort);

    @EntityGraph(attributePaths = {"customer", "site", "assignee"})
    Optional<WorkOrder> findWithRefsById(Long id);

    boolean existsByCustomerId(Long customerId);

    boolean existsBySiteId(Long siteId);

    boolean existsByAssigneeId(Long assigneeId);

    /** Candidates for the SLA sweep: still running, and with a deadline set. */
    @EntityGraph(attributePaths = {"assignee", "customer"})
    List<WorkOrder> findByStatusNotInAndSlaDueAtIsNotNull(Collection<WorkOrderStatus> statuses);

    List<WorkOrder> findByCompletedAtIsNotNullAndCompletedAtAfter(Instant since);

    /**
     * Race-free work-order numbers. A read-max-and-increment would let two
     * concurrent creates pick the same code; the sequence cannot.
     */
    @Query(value = "SELECT nextval('work_order_code_seq')", nativeQuery = true)
    long nextCodeSequence();
}
