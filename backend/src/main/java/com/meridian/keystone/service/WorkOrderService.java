package com.meridian.keystone.service;

import com.meridian.keystone.common.PageableFactory;
import com.meridian.keystone.domain.Part;
import com.meridian.keystone.domain.PartUsage;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.Site;
import com.meridian.keystone.domain.TimeLog;
import com.meridian.keystone.domain.User;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.domain.WorkOrderStatusHistory;
import com.meridian.keystone.dto.AssignRequest;
import com.meridian.keystone.dto.BoardColumn;
import com.meridian.keystone.dto.BoardView;
import com.meridian.keystone.dto.CreateWorkOrderRequest;
import com.meridian.keystone.dto.LogPartRequest;
import com.meridian.keystone.dto.LogTimeRequest;
import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.PartUsageView;
import com.meridian.keystone.dto.StatusHistoryView;
import com.meridian.keystone.dto.TimeLogView;
import com.meridian.keystone.dto.TransitionRequest;
import com.meridian.keystone.dto.UpdateWorkOrderRequest;
import com.meridian.keystone.dto.WorkOrderDetail;
import com.meridian.keystone.dto.WorkOrderFilter;
import com.meridian.keystone.dto.WorkOrderSummary;
import com.meridian.keystone.exception.BusinessRuleException;
import com.meridian.keystone.exception.ResourceNotFoundException;
import com.meridian.keystone.repository.PartRepository;
import com.meridian.keystone.repository.PartUsageRepository;
import com.meridian.keystone.repository.TimeLogRepository;
import com.meridian.keystone.repository.UserRepository;
import com.meridian.keystone.repository.WorkOrderRepository;
import com.meridian.keystone.repository.WorkOrderStatusHistoryRepository;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.security.WorkOrderAccess;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The heart of KEYSTONE: work-order reads, writes, the governed lifecycle, and
 * the transactional work log.
 *
 * <p>Three rules hold everywhere in this class:
 *
 * <ol>
 *   <li><b>Every read is scoped.</b> Lists go through
 *       {@link WorkOrderAccess#scope}; single reads through
 *       {@link WorkOrderAccess#requireView}. A technician cannot reach another
 *       technician's job by guessing an id.</li>
 *   <li><b>Every status change passes two gates</b> — the lifecycle map
 *       ({@code from.canTransitionTo(to)}, else 409) and the caller's
 *       permission ({@link WorkOrderLifecycle#canMove}, else 403) — and writes
 *       an append-only history row in the same transaction.</li>
 *   <li><b>Totals are recomputed from the ledger</b>, never incremented in
 *       place, so the displayed cost and labour can never drift from the parts
 *       and time rows that justify them.</li>
 * </ol>
 */
@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    /** Sort keys a client may use; anything else is a clean 400. */
    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "code", "title", "priority", "status", "slaDueAt", "slaStatus",
            "completedAt", "createdAt", "updatedAt");

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    /** Board columns stay readable; the column count is still the true total. */
    private static final int BOARD_COLUMN_LIMIT = 50;

    /** Most urgent first, then soonest deadline — how a dispatcher reads a board. */
    private static final Comparator<WorkOrder> TRIAGE_ORDER = Comparator
            .comparingInt((WorkOrder wo) -> -wo.getPriority().ordinal())
            .thenComparing((WorkOrder wo) -> wo.getSlaDueAt() == null
                    ? Instant.MAX
                    : wo.getSlaDueAt())
            .thenComparing(WorkOrder::getCode);

    private final WorkOrderRepository workOrders;
    private final WorkOrderStatusHistoryRepository history;
    private final PartRepository parts;
    private final PartUsageRepository partUsage;
    private final TimeLogRepository timeLogs;
    private final UserRepository users;
    private final SiteService siteService;
    private final WorkOrderAccess access;
    private final NotificationService notifications;

    public WorkOrderService(WorkOrderRepository workOrders,
                            WorkOrderStatusHistoryRepository history,
                            PartRepository parts,
                            PartUsageRepository partUsage,
                            TimeLogRepository timeLogs,
                            UserRepository users,
                            SiteService siteService,
                            WorkOrderAccess access,
                            NotificationService notifications) {
        this.workOrders = workOrders;
        this.history = history;
        this.parts = parts;
        this.partUsage = partUsage;
        this.timeLogs = timeLogs;
        this.users = users;
        this.siteService = siteService;
        this.access = access;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ reads

    /** Paged, filtered, searchable list — always intersected with the caller's scope. */
    public PageResponse<WorkOrderSummary> search(WorkOrderFilter filter,
                                                 Integer page,
                                                 Integer size,
                                                 String sort,
                                                 KeystoneUserDetails me) {
        Pageable pageable = PageableFactory.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        Specification<WorkOrder> spec = access.scope(me)
                .and(WorkOrderSpecifications.from(filter));
        return PageResponse.from(workOrders.findAll(spec, pageable), WorkOrderSummary::from);
    }

    /** Kanban board: one column per lifecycle status, scoped and triage-ordered. */
    public BoardView board(WorkOrderFilter filter, KeystoneUserDetails me) {
        Specification<WorkOrder> spec = access.scope(me)
                .and(WorkOrderSpecifications.from(filter));
        List<WorkOrder> visible = workOrders.findAll(spec, DEFAULT_SORT);

        Map<WorkOrderStatus, List<WorkOrder>> grouped = new LinkedHashMap<>();
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            grouped.put(status, new ArrayList<>());
        }
        for (WorkOrder wo : visible) {
            grouped.get(wo.getStatus()).add(wo);
        }

        List<BoardColumn> columns = new ArrayList<>();
        for (Map.Entry<WorkOrderStatus, List<WorkOrder>> entry : grouped.entrySet()) {
            List<WorkOrder> items = new ArrayList<>(entry.getValue());
            items.sort(TRIAGE_ORDER);
            List<WorkOrderSummary> capped = items.stream()
                    .limit(BOARD_COLUMN_LIMIT)
                    .map(WorkOrderSummary::from)
                    .toList();
            columns.add(new BoardColumn(entry.getKey(), items.size(), capped));
        }
        return new BoardView(columns);
    }

    /** The technician's field view: my open jobs, most urgent first. */
    public List<WorkOrderSummary> myWork(KeystoneUserDetails me) {
        Specification<WorkOrder> spec = access.scope(me)
                .and(WorkOrderSpecifications.assigneeIs(me.getId()))
                .and(WorkOrderSpecifications.openOnly());
        List<WorkOrder> mine = new ArrayList<>(workOrders.findAll(spec, DEFAULT_SORT));
        mine.sort(TRIAGE_ORDER);
        return mine.stream().map(WorkOrderSummary::from).toList();
    }

    public WorkOrderDetail detail(Long id, KeystoneUserDetails me) {
        return toDetail(loadVisible(id, me), me);
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public WorkOrderDetail create(CreateWorkOrderRequest request, KeystoneUserDetails me) {
        if (me.getRole() == Role.TECHNICIAN) {
            throw new AccessDeniedException("Technicians cannot raise work orders.");
        }
        // loadVisible is what stops a portal user filing work against
        // another customer's site — the customer is then derived from the site,
        // never taken from the request body.
        Site site = siteService.loadVisible(request.siteId(), me);
        Instant now = Instant.now();

        WorkOrder wo = new WorkOrder();
        wo.setCode(nextCode(now));
        wo.setTitle(request.title().trim());
        wo.setDescription(trimToNull(request.description()));
        wo.setPriority(request.priority());
        wo.setStatus(WorkOrderStatus.NEW);
        wo.setSite(site);
        wo.setCustomer(site.getCustomer());
        wo.setSlaDueAt(SlaPolicy.dueAt(request.priority(), now));
        wo.setSlaStatus(SlaPolicy.evaluate(now, wo.getSlaDueAt(), null, WorkOrderStatus.NEW, now));
        wo.setTotalLaborMinutes(0);
        wo.setTotalPartsCost(BigDecimal.ZERO);

        WorkOrder saved = workOrders.save(wo);
        User actor = actor(me);
        recordHistory(saved, null, WorkOrderStatus.NEW, actor, "Work order raised.");

        // Dispatchers and managers may assign in the same breath.
        if (request.assigneeId() != null) {
            if (!access.canAssign(me)) {
                throw new AccessDeniedException("You cannot assign work orders.");
            }
            User technician = loadAssignableTechnician(request.assigneeId());
            saved.setAssignee(technician);
            saved.setStatus(WorkOrderStatus.ASSIGNED);
            recordHistory(saved, WorkOrderStatus.NEW, WorkOrderStatus.ASSIGNED, actor,
                    "Assigned to " + technician.getFullName() + " at creation.");
            notifications.notifyAssignment(saved, technician, me.getId());
        }
        return toDetail(workOrders.save(saved), me);
    }

    @Transactional
    public WorkOrderDetail update(Long id, UpdateWorkOrderRequest request, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        if (!access.canEdit(me)) {
            throw new AccessDeniedException("You cannot edit work orders.");
        }
        if (wo.getStatus().isTerminal()) {
            throw new BusinessRuleException(
                    "This work order is " + wo.getStatus() + " and can no longer be edited.");
        }

        Site site = siteService.loadVisible(request.siteId(), me);
        // Re-homing a job to a different customer would rewrite who it belongs
        // to — and who can see it. Correcting the site within a customer is fine.
        if (!site.getCustomer().getId().equals(wo.getCustomer().getId())) {
            throw new BusinessRuleException(
                    "A work order cannot be moved to a different customer's site.");
        }

        wo.setTitle(request.title().trim());
        wo.setDescription(trimToNull(request.description()));
        wo.setSite(site);
        if (request.priority() != wo.getPriority()) {
            wo.setPriority(request.priority());
            // The deadline follows the priority, measured from when it was raised.
            wo.setSlaDueAt(SlaPolicy.dueAt(request.priority(), wo.getCreatedAt()));
        }
        refreshSla(wo, Instant.now());
        return toDetail(workOrders.save(wo), me);
    }

    @Transactional
    public WorkOrderDetail assign(Long id, AssignRequest request, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        if (!access.canAssign(me)) {
            throw new AccessDeniedException("You cannot assign work orders.");
        }
        if (wo.getStatus().isTerminal() || wo.getStatus() == WorkOrderStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "This work order is " + wo.getStatus() + " and cannot be assigned.");
        }
        User technician = loadAssignableTechnician(request.assigneeId());
        User previous = wo.getAssignee();
        if (previous != null && previous.getId().equals(technician.getId())) {
            throw new BusinessRuleException(
                    "This work order is already assigned to " + technician.getFullName() + ".");
        }

        WorkOrderStatus from = wo.getStatus();
        // Assigning a brand-new job is also its first lifecycle move.
        WorkOrderStatus to = from == WorkOrderStatus.NEW ? WorkOrderStatus.ASSIGNED : from;
        String note = previous == null
                ? "Assigned to " + technician.getFullName() + "."
                : "Reassigned from " + previous.getFullName()
                        + " to " + technician.getFullName() + ".";

        wo.setAssignee(technician);
        wo.setStatus(to);
        refreshSla(wo, Instant.now());
        WorkOrder saved = workOrders.save(wo);

        recordHistory(saved, from, to, actor(me), appendNote(note, request.note()));
        notifications.notifyAssignment(saved, technician, me.getId());
        return toDetail(saved, me);
    }

    @Transactional
    public WorkOrderDetail unassign(Long id, String note, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        if (!access.canAssign(me)) {
            throw new AccessDeniedException("You cannot unassign work orders.");
        }
        // ASSIGNED -> NEW is the only route back to the unassigned queue.
        if (wo.getStatus() != WorkOrderStatus.ASSIGNED) {
            throw new BusinessRuleException(
                    "Only a work order in ASSIGNED can be returned to the queue; this one is "
                            + wo.getStatus() + ".");
        }
        User previous = wo.getAssignee();
        wo.setAssignee(null);
        wo.setStatus(WorkOrderStatus.NEW);
        refreshSla(wo, Instant.now());
        WorkOrder saved = workOrders.save(wo);

        String message = previous == null
                ? "Returned to the unassigned queue."
                : "Unassigned from " + previous.getFullName() + ".";
        recordHistory(saved, WorkOrderStatus.ASSIGNED, WorkOrderStatus.NEW,
                actor(me), appendNote(message, note));
        return toDetail(saved, me);
    }

    /**
     * The governed status change. Structural legality first (409), then who is
     * allowed to make that particular move (403), then the record, its audit row
     * and its notifications commit together.
     */
    @Transactional
    public WorkOrderDetail transition(Long id, TransitionRequest request, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        WorkOrderStatus from = wo.getStatus();
        WorkOrderStatus to = request.targetStatus();

        if (from == to) {
            throw new BusinessRuleException("This work order is already " + from + ".");
        }
        if (!from.canTransitionTo(to)) {
            throw new BusinessRuleException(
                    "Cannot move a work order from " + from + " to " + to + ". Allowed from "
                            + from + ": " + describe(from.allowedTargets()) + ".");
        }
        boolean isAssignee = access.isAssignee(wo, me);
        if (!WorkOrderLifecycle.canMove(me.getRole(), isAssignee, from, to)) {
            throw new AccessDeniedException(
                    "Your role cannot move a work order from " + from + " to " + to + ".");
        }
        // Pausing or cancelling work is a decision someone has to justify.
        if ((to == WorkOrderStatus.ON_HOLD || to == WorkOrderStatus.CANCELLED)
                && (request.note() == null || request.note().isBlank())) {
            throw new BusinessRuleException("A note explaining the " + to + " is required.");
        }
        // Nothing is "done" with no work recorded against it.
        if (to == WorkOrderStatus.COMPLETED
                && wo.getTotalLaborMinutes() <= 0
                && wo.getTotalPartsCost().compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessRuleException(
                    "Log the time or parts spent on this job before completing it.");
        }

        Instant now = Instant.now();
        wo.setStatus(to);
        if (to == WorkOrderStatus.COMPLETED) {
            wo.setCompletedAt(now);
        } else if (to == WorkOrderStatus.IN_PROGRESS || to == WorkOrderStatus.CANCELLED) {
            // Reopening or cancelling un-freezes the SLA outcome.
            wo.setCompletedAt(null);
        }
        refreshSla(wo, now);
        WorkOrder saved = workOrders.save(wo);

        recordHistory(saved, from, to, actor(me), trimToNull(request.note()));
        notifications.notifyStatusChange(saved, from, to, me.getId());
        return toDetail(saved, me);
    }

    // -------------------------------------------------------------- work logs

    /**
     * Consume stock against a job. The part row is locked for the duration, so
     * two technicians claiming the last unit cannot both succeed: one commits,
     * the other gets a 409. Stock, the usage row and the job total move together
     * or not at all.
     */
    @Transactional
    public WorkOrderDetail logPart(Long id, LogPartRequest request, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        requireCanLogWork(wo, me);

        int quantity = request.quantity();
        Part part = parts.findByIdForUpdate(request.partId())
                .orElseThrow(() -> ResourceNotFoundException.of("Part", request.partId()));
        if (part.getStockQuantity() < quantity) {
            throw new BusinessRuleException("Only " + part.getStockQuantity() + " × "
                    + part.getSku() + " left in stock; you asked for " + quantity + ".");
        }
        part.setStockQuantity(part.getStockQuantity() - quantity);
        parts.save(part);

        PartUsage usage = new PartUsage();
        usage.setWorkOrder(wo);
        usage.setPart(part);
        usage.setQuantity(quantity);
        // Freeze the price at the moment of use so history stays honest when
        // the catalogue price later changes.
        usage.setUnitCostAtUse(part.getUnitCost());
        usage.setLoggedBy(actor(me));
        partUsage.save(usage);
        partUsage.flush();

        recomputeTotals(wo);
        return toDetail(workOrders.save(wo), me);
    }

    /** Reverse a mis-keyed part line: stock goes back, the total is recomputed. */
    @Transactional
    public WorkOrderDetail removePart(Long id, Long usageId, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        requireCanLogWork(wo, me);

        PartUsage usage = partUsage.findByIdAndWorkOrderId(usageId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("Part usage", usageId));
        Part part = parts.findByIdForUpdate(usage.getPart().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Part", usage.getPart().getId()));
        part.setStockQuantity(part.getStockQuantity() + usage.getQuantity());
        parts.save(part);

        partUsage.delete(usage);
        partUsage.flush();

        recomputeTotals(wo);
        return toDetail(workOrders.save(wo), me);
    }

    @Transactional
    public WorkOrderDetail logTime(Long id, LogTimeRequest request, KeystoneUserDetails me) {
        WorkOrder wo = loadVisible(id, me);
        requireCanLogWork(wo, me);

        TimeLog log = new TimeLog();
        log.setWorkOrder(wo);
        log.setTechnician(actor(me));
        log.setMinutes(request.minutes());
        log.setNote(trimToNull(request.note()));
        timeLogs.save(log);
        timeLogs.flush();

        recomputeTotals(wo);
        return toDetail(workOrders.save(wo), me);
    }

    // ---------------------------------------------------------------- helpers

    private void requireCanLogWork(WorkOrder wo, KeystoneUserDetails me) {
        if (access.canLogWork(wo, me)) {
            return;
        }
        // Distinguish "not your job" from "not the right time" — the second is a
        // state problem, not a permissions problem, and the message should say so.
        if (!WorkOrderLifecycle.acceptsWorkLogs(wo.getStatus())) {
            throw new BusinessRuleException(
                    "Parts and time can only be logged while a job is IN_PROGRESS or ON_HOLD; "
                            + "this one is " + wo.getStatus() + ".");
        }
        throw new AccessDeniedException(
                "Only the assigned technician or a manager can log work on this job.");
    }

    /**
     * Rebuild the job's totals from its parts and time rows. Recomputing rather
     * than incrementing means the summary can never disagree with the ledger.
     */
    private void recomputeTotals(WorkOrder wo) {
        BigDecimal partsCost = partUsage.findByWorkOrderIdOrderByCreatedAtAsc(wo.getId()).stream()
                .map(usage -> usage.getUnitCostAtUse()
                        .multiply(BigDecimal.valueOf(usage.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        int minutes = timeLogs.findByWorkOrderIdOrderByCreatedAtAsc(wo.getId()).stream()
                .mapToInt(TimeLog::getMinutes)
                .sum();
        wo.setTotalPartsCost(partsCost);
        wo.setTotalLaborMinutes(minutes);
    }

    private WorkOrder loadVisible(Long id, KeystoneUserDetails me) {
        WorkOrder wo = workOrders.findWithRefsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Work order", id));
        access.requireView(wo, me);
        return wo;
    }

    private User loadAssignableTechnician(Long id) {
        User technician = users.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));
        if (technician.getRole() != Role.TECHNICIAN) {
            throw new BusinessRuleException(
                    technician.getFullName() + " is not a technician.");
        }
        if (!technician.isActive()) {
            throw new BusinessRuleException(
                    technician.getFullName() + "'s account is not active.");
        }
        return technician;
    }

    /**
     * The acting user as a managed entity. Read fresh rather than reusing the
     * detached copy from the security context, so audit rows always reference a
     * row this transaction knows about.
     */
    private User actor(KeystoneUserDetails me) {
        return users.findById(me.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", me.getId()));
    }

    private void recordHistory(WorkOrder wo,
                               WorkOrderStatus from,
                               WorkOrderStatus to,
                               User actor,
                               String note) {
        WorkOrderStatusHistory row = new WorkOrderStatusHistory();
        row.setWorkOrder(wo);
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setChangedBy(actor);
        row.setNote(note);
        history.save(row);
    }

    private void refreshSla(WorkOrder wo, Instant now) {
        Instant windowStart = wo.getCreatedAt() == null ? now : wo.getCreatedAt();
        wo.setSlaStatus(SlaPolicy.evaluate(
                windowStart, wo.getSlaDueAt(), wo.getCompletedAt(), wo.getStatus(), now));
    }

    /**
     * Next human-readable code, e.g. WO-2026-0004. The number comes from a
     * database sequence rather than max(code)+1, so two dispatchers creating
     * work at the same moment cannot collide on the unique index.
     */
    private String nextCode(Instant now) {
        long sequence = workOrders.nextCodeSequence();
        int year = now.atZone(ZoneOffset.UTC).getYear();
        return String.format("WO-%d-%04d", year, sequence);
    }

    private WorkOrderDetail toDetail(WorkOrder wo, KeystoneUserDetails me) {
        List<StatusHistoryView> timeline =
                history.findByWorkOrderIdOrderByCreatedAtAsc(wo.getId()).stream()
                        .map(StatusHistoryView::from)
                        .toList();
        List<PartUsageView> partLines =
                partUsage.findByWorkOrderIdOrderByCreatedAtAsc(wo.getId()).stream()
                        .map(PartUsageView::from)
                        .toList();
        List<TimeLogView> timeEntries =
                timeLogs.findByWorkOrderIdOrderByCreatedAtAsc(wo.getId()).stream()
                        .map(TimeLogView::from)
                        .toList();
        boolean isAssignee = access.isAssignee(wo, me);
        return WorkOrderDetail.of(
                wo,
                timeline,
                partLines,
                timeEntries,
                WorkOrderLifecycle.allowedTransitions(me.getRole(), isAssignee, wo.getStatus()),
                access.canEdit(me) && wo.getStatus().isOpen(),
                access.canAssign(me),
                access.canLogWork(wo, me));
    }

    private String describe(Set<WorkOrderStatus> targets) {
        if (targets.isEmpty()) {
            return "nothing (it is a terminal state)";
        }
        return targets.stream().map(Enum::name).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    private String appendNote(String message, String userNote) {
        if (userNote == null || userNote.isBlank()) {
            return message;
        }
        return message + " " + userNote.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
