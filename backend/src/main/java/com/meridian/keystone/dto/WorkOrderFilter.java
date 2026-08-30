package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrderStatus;

import java.util.List;

/**
 * Everything a client may narrow a work-order list by. Every field is optional;
 * null means "don't filter on this".
 *
 * <p>Note this is only a filter — never a security boundary. The caller's own
 * visibility scope is ANDed on top of it server-side, so passing
 * {@code customerId} for someone else's customer returns nothing rather than
 * someone else's data.
 */
public record WorkOrderFilter(
        List<WorkOrderStatus> statuses,
        Priority priority,
        SlaStatus slaStatus,
        Long assigneeId,
        Long customerId,
        Long siteId,
        Boolean unassigned,
        Boolean openOnly,
        String search) {
}
