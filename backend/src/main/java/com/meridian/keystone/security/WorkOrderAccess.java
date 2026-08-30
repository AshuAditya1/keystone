package com.meridian.keystone.security;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.service.WorkOrderLifecycle;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Who can see and touch which work orders.
 *
 * <p>Visibility is expressed two ways that must always agree: as a
 * {@link Specification} that constrains list queries at the database, and as a
 * per-record check for single-record reads. Both live here so a new endpoint
 * cannot accidentally invent its own weaker rule.
 *
 * <ul>
 *   <li><b>MANAGER, DISPATCHER</b> — the whole operation.</li>
 *   <li><b>TECHNICIAN</b> — only jobs assigned to them.</li>
 *   <li><b>CUSTOMER</b> — only jobs belonging to their own customer.</li>
 * </ul>
 */
@Component
public class WorkOrderAccess {

    /** Predicate limiting a query to what this caller may see. */
    public Specification<WorkOrder> scope(KeystoneUserDetails me) {
        return switch (me.getRole()) {
            case MANAGER, DISPATCHER -> (root, query, cb) -> cb.conjunction();
            case TECHNICIAN -> (root, query, cb) ->
                    cb.equal(root.get("assignee").get("id"), me.getId());
            case CUSTOMER -> me.getCustomerId() == null
                    // A customer account with no customer linked can see nothing,
                    // rather than defaulting to everything.
                    ? (root, query, cb) -> cb.disjunction()
                    : (root, query, cb) ->
                            cb.equal(root.get("customer").get("id"), me.getCustomerId());
        };
    }

    /** Whether this caller may read this specific work order. */
    public boolean canView(WorkOrder wo, KeystoneUserDetails me) {
        return switch (me.getRole()) {
            case MANAGER, DISPATCHER -> true;
            case TECHNICIAN -> isAssignee(wo, me);
            case CUSTOMER -> me.getCustomerId() != null
                    && me.getCustomerId().equals(wo.getCustomer().getId());
        };
    }

    public void requireView(WorkOrder wo, KeystoneUserDetails me) {
        if (!canView(wo, me)) {
            throw new AccessDeniedException("You do not have access to this work order.");
        }
    }

    public boolean isAssignee(WorkOrder wo, KeystoneUserDetails me) {
        return wo.getAssignee() != null && wo.getAssignee().getId().equals(me.getId());
    }

    /** Editing the record's details (title, priority, site) — the office roles. */
    public boolean canEdit(KeystoneUserDetails me) {
        return me.getRole() == Role.MANAGER || me.getRole() == Role.DISPATCHER;
    }

    /** Assigning or unassigning a technician — the dispatch desk. */
    public boolean canAssign(KeystoneUserDetails me) {
        return me.getRole() == Role.MANAGER || me.getRole() == Role.DISPATCHER;
    }

    /**
     * Logging parts and time: the manager or the assigned technician, and only
     * while the job is actually underway.
     */
    public boolean canLogWork(WorkOrder wo, KeystoneUserDetails me) {
        boolean rolePermits = me.getRole() == Role.MANAGER
                || (me.getRole() == Role.TECHNICIAN && isAssignee(wo, me));
        return rolePermits && WorkOrderLifecycle.acceptsWorkLogs(wo.getStatus());
    }
}
