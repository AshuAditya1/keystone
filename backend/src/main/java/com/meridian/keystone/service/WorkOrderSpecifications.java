package com.meridian.keystone.service;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.dto.WorkOrderFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Composable query predicates for work-order search.
 *
 * <p>Association filters compare the foreign key ({@code assignee.id}) rather
 * than joining, which keeps every predicate join-free and therefore safe to
 * combine with the repository's fetch graph.
 */
public final class WorkOrderSpecifications {

    private WorkOrderSpecifications() {
    }

    private static Specification<WorkOrder> alwaysTrue() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<WorkOrder> statusIn(List<WorkOrderStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<WorkOrder> priorityIs(Priority priority) {
        return (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<WorkOrder> slaStatusIs(SlaStatus slaStatus) {
        return (root, query, cb) -> cb.equal(root.get("slaStatus"), slaStatus);
    }

    public static Specification<WorkOrder> assigneeIs(Long assigneeId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    public static Specification<WorkOrder> customerIs(Long customerId) {
        return (root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<WorkOrder> siteIs(Long siteId) {
        return (root, query, cb) -> cb.equal(root.get("site").get("id"), siteId);
    }

    public static Specification<WorkOrder> isUnassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignee"));
    }

    /** Excludes the terminal states — the live queue. */
    public static Specification<WorkOrder> openOnly() {
        return (root, query, cb) -> cb.not(root.get("status")
                .in(List.of(WorkOrderStatus.CLOSED, WorkOrderStatus.CANCELLED)));
    }

    /** Case-insensitive match across code, title and description. */
    public static Specification<WorkOrder> textSearch(String term) {
        String pattern = "%" + term.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern),
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    /** Fold a filter into a single predicate, skipping absent fields. */
    public static Specification<WorkOrder> from(WorkOrderFilter filter) {
        Specification<WorkOrder> spec = alwaysTrue();
        if (filter == null) {
            return spec;
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            spec = spec.and(statusIn(filter.statuses()));
        }
        if (filter.priority() != null) {
            spec = spec.and(priorityIs(filter.priority()));
        }
        if (filter.slaStatus() != null) {
            spec = spec.and(slaStatusIs(filter.slaStatus()));
        }
        if (filter.assigneeId() != null) {
            spec = spec.and(assigneeIs(filter.assigneeId()));
        }
        if (filter.customerId() != null) {
            spec = spec.and(customerIs(filter.customerId()));
        }
        if (filter.siteId() != null) {
            spec = spec.and(siteIs(filter.siteId()));
        }
        if (Boolean.TRUE.equals(filter.unassigned())) {
            spec = spec.and(isUnassigned());
        }
        if (Boolean.TRUE.equals(filter.openOnly())) {
            spec = spec.and(openOnly());
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            spec = spec.and(textSearch(filter.search()));
        }
        return spec;
    }
}
