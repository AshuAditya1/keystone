package com.meridian.keystone.controller;

import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.dto.AssignRequest;
import com.meridian.keystone.dto.BoardView;
import com.meridian.keystone.dto.CreateWorkOrderRequest;
import com.meridian.keystone.dto.LogPartRequest;
import com.meridian.keystone.dto.LogTimeRequest;
import com.meridian.keystone.dto.NoteRequest;
import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.TransitionRequest;
import com.meridian.keystone.dto.UpdateWorkOrderRequest;
import com.meridian.keystone.dto.WorkOrderDetail;
import com.meridian.keystone.dto.WorkOrderFilter;
import com.meridian.keystone.dto.WorkOrderSummary;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Work-order API — the core of the system.
 *
 * <p>{@code @PreAuthorize} here is a coarse first gate on <em>role</em>. The
 * fine-grained rules — which records you may see, and which lifecycle moves you
 * may make on this particular job — live in the service and are re-checked on
 * every call. Nothing is trusted from the client, and no rule is enforced only
 * by the UI hiding a button.
 */
@RestController
@RequestMapping("/api/work-orders")
@Tag(name = "Work orders")
public class WorkOrderController {

    private final WorkOrderService workOrders;

    public WorkOrderController(WorkOrderService workOrders) {
        this.workOrders = workOrders;
    }

    @GetMapping
    @Operation(summary = "List work orders",
            description = "Paged, filterable, searchable. Results are always intersected with "
                    + "the caller's own visibility scope: technicians see only their assigned "
                    + "jobs, customers only their own sites.")
    public PageResponse<WorkOrderSummary> list(
            @RequestParam(required = false) List<WorkOrderStatus> statuses,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) Boolean openOnly,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal KeystoneUserDetails me) {

        WorkOrderFilter filter = new WorkOrderFilter(statuses, priority, slaStatus,
                assigneeId, customerId, siteId, unassigned, openOnly, search);
        return workOrders.search(filter, page, size, sort, me);
    }

    @GetMapping("/board")
    @Operation(summary = "Kanban board",
            description = "One column per lifecycle status, ordered most urgent first.")
    public BoardView board(
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Boolean unassigned,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal KeystoneUserDetails me) {

        WorkOrderFilter filter = new WorkOrderFilter(null, priority, null,
                assigneeId, customerId, null, unassigned, null, search);
        return workOrders.board(filter, me);
    }

    @GetMapping("/my")
    @Operation(summary = "My open jobs",
            description = "The technician field view: jobs assigned to the caller that are "
                    + "still open, most urgent first.")
    public List<WorkOrderSummary> myWork(@AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.myWork(me);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Work-order detail",
            description = "Includes the audit trail, parts, time, and the exact set of "
                    + "lifecycle actions this caller is allowed to take next.")
    public WorkOrderDetail detail(@PathVariable Long id,
                                  @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.detail(id, me);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'CUSTOMER')")
    @Operation(summary = "Raise a work order",
            description = "Customers may raise requests against their own sites only; the "
                    + "owning customer is derived from the site, never from the request body.")
    public ResponseEntity<WorkOrderDetail> create(
            @Valid @RequestBody CreateWorkOrderRequest request,
            @AuthenticationPrincipal KeystoneUserDetails me) {
        WorkOrderDetail created = workOrders.create(request, me);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Edit a work order",
            description = "Title, description, priority and site. Changing the priority "
                    + "recalculates the SLA deadline from when the job was raised.")
    public WorkOrderDetail update(@PathVariable Long id,
                                  @Valid @RequestBody UpdateWorkOrderRequest request,
                                  @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.update(id, request, me);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Assign to a technician",
            description = "Assigning a NEW job also moves it to ASSIGNED. Reassignment is "
                    + "recorded in the audit trail.")
    public WorkOrderDetail assign(@PathVariable Long id,
                                  @Valid @RequestBody AssignRequest request,
                                  @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.assign(id, request, me);
    }

    @PostMapping("/{id}/unassign")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Return to the unassigned queue",
            description = "Only valid from ASSIGNED; moves the job back to NEW.")
    public WorkOrderDetail unassign(@PathVariable Long id,
                                    @Valid @RequestBody(required = false) NoteRequest request,
                                    @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.unassign(id, request == null ? null : request.note(), me);
    }

    @PostMapping("/{id}/transition")
    @Operation(summary = "Move through the lifecycle",
            description = "Rejects an illegal move with 409 and a move this role may not make "
                    + "with 403. Every accepted move writes an append-only history row.")
    public WorkOrderDetail transition(@PathVariable Long id,
                                      @Valid @RequestBody TransitionRequest request,
                                      @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.transition(id, request, me);
    }

    @PostMapping("/{id}/parts")
    @PreAuthorize("hasAnyRole('MANAGER', 'TECHNICIAN')")
    @Operation(summary = "Log parts used",
            description = "Decrements stock inside the same transaction. Returns 409 if there "
                    + "is not enough stock — the level can never go negative.")
    public WorkOrderDetail logPart(@PathVariable Long id,
                                   @Valid @RequestBody LogPartRequest request,
                                   @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.logPart(id, request, me);
    }

    @DeleteMapping("/{id}/parts/{usageId}")
    @PreAuthorize("hasAnyRole('MANAGER', 'TECHNICIAN')")
    @Operation(summary = "Reverse a parts line",
            description = "Returns the stock and recomputes the job's parts cost.")
    public WorkOrderDetail removePart(@PathVariable Long id,
                                      @PathVariable Long usageId,
                                      @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.removePart(id, usageId, me);
    }

    @PostMapping("/{id}/time")
    @PreAuthorize("hasAnyRole('MANAGER', 'TECHNICIAN')")
    @Operation(summary = "Log time spent",
            description = "Only while the job is IN_PROGRESS or ON_HOLD, and only by the "
                    + "assigned technician or a manager.")
    public WorkOrderDetail logTime(@PathVariable Long id,
                                   @Valid @RequestBody LogTimeRequest request,
                                   @AuthenticationPrincipal KeystoneUserDetails me) {
        return workOrders.logTime(id, request, me);
    }
}
