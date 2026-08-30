package com.meridian.keystone.service;

import com.meridian.keystone.domain.Customer;
import com.meridian.keystone.domain.Part;
import com.meridian.keystone.domain.PartUsage;
import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.Site;
import com.meridian.keystone.domain.TimeLog;
import com.meridian.keystone.domain.User;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.domain.WorkOrderStatusHistory;
import com.meridian.keystone.dto.AssignRequest;
import com.meridian.keystone.dto.CreateWorkOrderRequest;
import com.meridian.keystone.dto.LogPartRequest;
import com.meridian.keystone.dto.LogTimeRequest;
import com.meridian.keystone.dto.TransitionRequest;
import com.meridian.keystone.dto.UpdateWorkOrderRequest;
import com.meridian.keystone.dto.WorkOrderDetail;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.meridian.keystone.support.TestFixtures.NOW;
import static com.meridian.keystone.support.TestFixtures.customer;
import static com.meridian.keystone.support.TestFixtures.part;
import static com.meridian.keystone.support.TestFixtures.portalUser;
import static com.meridian.keystone.support.TestFixtures.principal;
import static com.meridian.keystone.support.TestFixtures.site;
import static com.meridian.keystone.support.TestFixtures.user;
import static com.meridian.keystone.support.TestFixtures.workOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The service-layer business rules, with the repositories mocked.
 *
 * <p>{@link WorkOrderAccess} is deliberately <em>not</em> mocked. It is the
 * authorization logic under test as much as the service is, and stubbing it would
 * turn every permission assertion below into a test of the stub. The state machine
 * and the SLA policy are static and equally real; only the database, the site
 * lookup and the notification fan-out are faked.
 *
 * <p>Mockito runs lenient here on purpose: the fixtures in {@code setUp} stand up
 * a small but complete world (four staff accounts, a customer, a site) and most
 * tests use only part of it. Failing a test because it did not happen to need the
 * dispatcher account would be noise, not signal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkOrderServiceTest {

    private static final long WO_ID = 500L;

    @Mock private WorkOrderRepository workOrders;
    @Mock private WorkOrderStatusHistoryRepository history;
    @Mock private PartRepository parts;
    @Mock private PartUsageRepository partUsage;
    @Mock private TimeLogRepository timeLogs;
    @Mock private UserRepository users;
    @Mock private SiteService siteService;
    @Mock private NotificationService notifications;

    /** The real thing — see the class comment. */
    private final WorkOrderAccess access = new WorkOrderAccess();

    private WorkOrderService service;

    private Customer acme;
    private Site warehouse;
    private User manager;
    private User dispatcher;
    private User tech;
    private User otherTech;
    private KeystoneUserDetails asManager;
    private KeystoneUserDetails asDispatcher;
    private KeystoneUserDetails asTech;
    private KeystoneUserDetails asOtherTech;
    private KeystoneUserDetails asPortal;

    @BeforeEach
    void setUp() {
        service = new WorkOrderService(workOrders, history, parts, partUsage, timeLogs,
                users, siteService, access, notifications);

        acme = customer(1L, "Acme Retail");
        warehouse = site(10L, acme, "Acme Warehouse");

        manager = user(100L, Role.MANAGER, "Priya Raman");
        dispatcher = user(101L, Role.DISPATCHER, "Tom Reed");
        tech = user(102L, Role.TECHNICIAN, "Sam Okafor");
        otherTech = user(103L, Role.TECHNICIAN, "Lena Fischer");
        User portal = portalUser(104L, "Cara Bell", acme);

        asManager = principal(manager);
        asDispatcher = principal(dispatcher);
        asTech = principal(tech);
        asOtherTech = principal(otherTech);
        asPortal = principal(portal);

        when(users.findById(100L)).thenReturn(Optional.of(manager));
        when(users.findById(101L)).thenReturn(Optional.of(dispatcher));
        when(users.findById(102L)).thenReturn(Optional.of(tech));
        when(users.findById(103L)).thenReturn(Optional.of(otherTech));
        when(users.findById(104L)).thenReturn(Optional.of(portal));

        when(siteService.loadVisible(any(), any())).thenReturn(warehouse);
        // save() returns what it was given, as JPA would for an already-identified row.
        when(workOrders.save(any(WorkOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------------ reads

    @Test
    @DisplayName("a technician cannot reach another technician's job by guessing its id")
    void scopingIsEnforcedOnSingleReads() {
        staged(WorkOrderStatus.IN_PROGRESS, otherTech);

        assertThatThrownBy(() -> service.detail(WO_ID, asTech))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    @DisplayName("a customer sees their own work orders and only those")
    void portalScopingIsByCustomer() {
        staged(WorkOrderStatus.NEW, null);
        assertThat(service.detail(WO_ID, asPortal).customerId()).isEqualTo(1L);

        Customer rival = customer(2L, "Beta Foods");
        WorkOrder theirs = workOrder(501L, WorkOrderStatus.NEW, rival,
                site(11L, rival, "Beta Depot"), null);
        when(workOrders.findWithRefsById(501L)).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.detail(501L, asPortal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("an unknown id is a not-found, not an empty result")
    void unknownIdIsNotFound() {
        when(workOrders.findWithRefsById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(999L, asManager))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Work order");
    }

    @Test
    @DisplayName("the detail view tells the caller what they may do next")
    void detailCarriesTheCallersOwnActions() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        WorkOrderDetail forTech = service.detail(WO_ID, asTech);
        assertThat(forTech.allowedTransitions())
                .containsExactly(WorkOrderStatus.COMPLETED, WorkOrderStatus.ON_HOLD);
        assertThat(forTech.canLogWork()).isTrue();
        assertThat(forTech.canEdit()).isFalse();
        assertThat(forTech.canAssign()).isFalse();

        WorkOrderDetail forDispatcher = service.detail(WO_ID, asDispatcher);
        assertThat(forDispatcher.allowedTransitions())
                .containsExactly(WorkOrderStatus.CANCELLED);
        assertThat(forDispatcher.canLogWork()).isFalse();
        assertThat(forDispatcher.canEdit()).isTrue();
        assertThat(forDispatcher.canAssign()).isTrue();

        assertThat(service.detail(WO_ID, asPortal).allowedTransitions()).isEmpty();
    }

    // ------------------------------------------------------------ transitions

    @Test
    @DisplayName("a structurally illegal move is a conflict, and says what is allowed")
    void illegalMoveIsAConflict() {
        staged(WorkOrderStatus.NEW, null);

        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.COMPLETED, null), asManager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot move a work order from NEW to COMPLETED")
                .hasMessageContaining("ASSIGNED");

        verify(history, never()).save(any(WorkOrderStatusHistory.class));
        verify(notifications, never()).notifyStatusChange(any(), any(), any(), any());
    }

    @Test
    @DisplayName("moving to the status it is already in is refused before anything else")
    void redundantMoveIsRefused() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.IN_PROGRESS, null), asManager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This work order is already IN_PROGRESS.");
    }

    @Test
    @DisplayName("a legal move the caller's role cannot make is a 403, not a 409")
    void unauthorizedMoveIsForbidden() {
        staged(WorkOrderStatus.ASSIGNED, tech);

        // ASSIGNED -> IN_PROGRESS is a legal move; a dispatcher just isn't the one
        // who makes it. The distinction matters: the client should not retry.
        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.IN_PROGRESS, null), asDispatcher))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Your role cannot move a work order");

        verify(history, never()).save(any(WorkOrderStatusHistory.class));
    }

    @Test
    @DisplayName("the customer portal cannot drive the lifecycle")
    void portalCannotTransition() {
        staged(WorkOrderStatus.NEW, null);

        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.CANCELLED, "Not needed now."), asPortal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("pausing work requires a reason")
    void pausingRequiresANote() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.ON_HOLD, "   "), asTech))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A note explaining the ON_HOLD is required.");
    }

    @Test
    @DisplayName("cancelling work requires a reason")
    void cancellingRequiresANote() {
        staged(WorkOrderStatus.NEW, null);

        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.CANCELLED, null), asDispatcher))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A note explaining the CANCELLED is required.");
    }

    @Test
    @DisplayName("nothing is completed with no work recorded against it")
    void completingRequiresLoggedWork() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        assertThatThrownBy(() -> service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.COMPLETED, null), asTech))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Log the time or parts spent on this job before completing it.");
    }

    @Test
    @DisplayName("logged time alone is enough to complete a job")
    void completingWithTimeLoggedSucceeds() {
        WorkOrder wo = staged(WorkOrderStatus.IN_PROGRESS, tech);
        wo.setTotalLaborMinutes(90);

        WorkOrderDetail detail = service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.COMPLETED, "  Fan belt replaced.  "), asTech);

        assertThat(detail.status()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(wo.getCompletedAt()).isNotNull();

        WorkOrderStatusHistory row = capturedHistory();
        assertThat(row.getFromStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(row.getToStatus()).isEqualTo(WorkOrderStatus.COMPLETED);
        assertThat(row.getChangedBy()).isSameAs(tech);
        assertThat(row.getNote()).isEqualTo("Fan belt replaced.");
        assertThat(row.getWorkOrder()).isSameAs(wo);

        verify(notifications).notifyStatusChange(wo,
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED, tech.getId());
    }

    @Test
    @DisplayName("logged parts alone are also enough")
    void completingWithPartsLoggedSucceeds() {
        WorkOrder wo = staged(WorkOrderStatus.IN_PROGRESS, tech);
        wo.setTotalPartsCost(new BigDecimal("42.00"));

        assertThat(service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.COMPLETED, null), asTech).status())
                .isEqualTo(WorkOrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("reopening a completed job clears the completion stamp")
    void reopeningClearsTheCompletionStamp() {
        WorkOrder wo = staged(WorkOrderStatus.COMPLETED, tech);
        wo.setCompletedAt(NOW);

        service.transition(WO_ID, new TransitionRequest(
                WorkOrderStatus.IN_PROGRESS, "Customer says the noise is back."), asManager);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(wo.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("every accepted move leaves exactly one audit row behind")
    void everyMoveIsAudited() {
        WorkOrder wo = staged(WorkOrderStatus.COMPLETED, tech);
        wo.setCompletedAt(NOW);

        service.transition(WO_ID,
                new TransitionRequest(WorkOrderStatus.CLOSED, null), asManager);

        verify(history, times(1)).save(any(WorkOrderStatusHistory.class));
        assertThat(capturedHistory().getToStatus()).isEqualTo(WorkOrderStatus.CLOSED);
    }

    // -------------------------------------------------------------- work logs

    @Test
    @DisplayName("stock can never go negative: the request is refused, not clamped")
    void stockCannotGoNegative() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);
        Part pump = part(20L, "PMP-100", "12.50", 2);
        when(parts.findByIdForUpdate(20L)).thenReturn(Optional.of(pump));

        assertThatThrownBy(() -> service.logPart(WO_ID, new LogPartRequest(20L, 5), asTech))
                .isInstanceOf(BusinessRuleException.class)
                // Asserted in two halves so the test does not depend on the "×"
                // in the message surviving the source encoding.
                .hasMessageStartingWith("Only 2")
                .hasMessageEndingWith("PMP-100 left in stock; you asked for 5.");

        assertThat(pump.getStockQuantity()).isEqualTo(2);
        verify(partUsage, never()).save(any(PartUsage.class));
    }

    @Test
    @DisplayName("logging a part draws down stock and freezes the price at the moment of use")
    void loggingAPartDrawsDownStock() {
        WorkOrder wo = staged(WorkOrderStatus.IN_PROGRESS, tech);
        // A stale total, to prove the new one is not derived from it.
        wo.setTotalPartsCost(new BigDecimal("999.99"));
        wo.setTotalLaborMinutes(7);

        Part pump = part(20L, "PMP-100", "12.50", 10);
        when(parts.findByIdForUpdate(20L)).thenReturn(Optional.of(pump));
        when(partUsage.findByWorkOrderIdOrderByCreatedAtAsc(WO_ID))
                .thenReturn(List.of(usage(1L, pump, 3), usage(2L, pump, 1)));
        when(timeLogs.findByWorkOrderIdOrderByCreatedAtAsc(WO_ID))
                .thenReturn(List.of(timeLog(1L, 45), timeLog(2L, 45)));

        WorkOrderDetail detail = service.logPart(WO_ID, new LogPartRequest(20L, 3), asTech);

        assertThat(pump.getStockQuantity()).isEqualTo(7);

        ArgumentCaptor<PartUsage> captor = ArgumentCaptor.forClass(PartUsage.class);
        verify(partUsage).save(captor.capture());
        PartUsage saved = captor.getValue();
        assertThat(saved.getQuantity()).isEqualTo(3);
        assertThat(saved.getUnitCostAtUse()).isEqualByComparingTo("12.50");
        assertThat(saved.getLoggedBy()).isSameAs(tech);
        assertThat(saved.getWorkOrder()).isSameAs(wo);

        // Totals are rebuilt from the ledger (4 units at 12.50, 90 minutes),
        // not incremented onto whatever was there before.
        assertThat(detail.totalPartsCost()).isEqualByComparingTo("50.00");
        assertThat(detail.totalLaborMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("removing a mis-keyed part line puts the stock back")
    void removingAPartReturnsStock() {
        WorkOrder wo = staged(WorkOrderStatus.IN_PROGRESS, tech);
        wo.setTotalPartsCost(new BigDecimal("37.50"));

        Part pump = part(20L, "PMP-100", "12.50", 4);
        PartUsage existing = usage(7L, pump, 3);
        when(partUsage.findByIdAndWorkOrderId(7L, WO_ID)).thenReturn(Optional.of(existing));
        when(parts.findByIdForUpdate(20L)).thenReturn(Optional.of(pump));
        when(partUsage.findByWorkOrderIdOrderByCreatedAtAsc(WO_ID)).thenReturn(List.of());

        service.removePart(WO_ID, 7L, asTech);

        assertThat(pump.getStockQuantity()).isEqualTo(7);
        verify(partUsage).delete(existing);
        assertThat(wo.getTotalPartsCost()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("a part line belonging to a different job cannot be removed through this one")
    void cannotRemoveAnotherJobsPartLine() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);
        when(partUsage.findByIdAndWorkOrderId(7L, WO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removePart(WO_ID, 7L, asTech))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Part usage");
    }

    @Test
    @DisplayName("time can be logged while a job is paused, since the visit already happened")
    void timeCanBeLoggedOnHold() {
        staged(WorkOrderStatus.ON_HOLD, tech);
        when(timeLogs.findByWorkOrderIdOrderByCreatedAtAsc(WO_ID))
                .thenReturn(List.of(timeLog(1L, 30)));

        WorkOrderDetail detail = service.logTime(WO_ID,
                new LogTimeRequest(30, "  Waiting for a part.  "), asTech);

        ArgumentCaptor<TimeLog> captor = ArgumentCaptor.forClass(TimeLog.class);
        verify(timeLogs).save(captor.capture());
        assertThat(captor.getValue().getMinutes()).isEqualTo(30);
        assertThat(captor.getValue().getTechnician()).isSameAs(tech);
        assertThat(captor.getValue().getNote()).isEqualTo("Waiting for a part.");
        assertThat(detail.totalLaborMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("work cannot be logged against a job that has not started")
    void workLogsNeedAnActiveJob() {
        staged(WorkOrderStatus.ASSIGNED, tech);

        assertThatThrownBy(() -> service.logTime(WO_ID, new LogTimeRequest(30, null), asTech))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("IN_PROGRESS or ON_HOLD")
                .hasMessageContaining("this one is ASSIGNED");

        verify(timeLogs, never()).save(any(TimeLog.class));
    }

    @Test
    @DisplayName("a dispatcher can see the job but cannot log work on it")
    void dispatcherCannotLogWork() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        assertThatThrownBy(() -> service.logPart(WO_ID, new LogPartRequest(20L, 1), asDispatcher))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the assigned technician or a manager");

        verify(parts, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("a manager can log work on anyone's job, to correct the record")
    void managerCanLogWork() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);
        when(timeLogs.findByWorkOrderIdOrderByCreatedAtAsc(WO_ID))
                .thenReturn(List.of(timeLog(1L, 15)));

        service.logTime(WO_ID, new LogTimeRequest(15, "Correcting a missed entry."), asManager);

        ArgumentCaptor<TimeLog> captor = ArgumentCaptor.forClass(TimeLog.class);
        verify(timeLogs).save(captor.capture());
        assertThat(captor.getValue().getTechnician()).isSameAs(manager);
    }

    // ------------------------------------------------------------- create/edit

    @Test
    @DisplayName("technicians receive work; they do not raise it")
    void techniciansCannotCreate() {
        assertThatThrownBy(() -> service.create(new CreateWorkOrderRequest(
                "Chiller noise", null, Priority.HIGH, 10L, null), asTech))
                .isInstanceOf(AccessDeniedException.class);

        verify(siteService, never()).loadVisible(any(), any());
    }

    @Test
    @DisplayName("a new job takes its customer from the site, never from the request")
    void createDerivesTheCustomerFromTheSite() {
        when(workOrders.nextCodeSequence()).thenReturn(4L);

        WorkOrderDetail detail = service.create(new CreateWorkOrderRequest(
                "  Chiller unit making a noise  ", "   ", Priority.URGENT, 10L, null), asDispatcher);

        String year = String.valueOf(Instant.now().atZone(ZoneOffset.UTC).getYear());
        assertThat(detail.code()).isEqualTo("WO-" + year + "-0004");
        assertThat(detail.status()).isEqualTo(WorkOrderStatus.NEW);
        assertThat(detail.customerId()).isEqualTo(acme.getId());
        assertThat(detail.siteId()).isEqualTo(warehouse.getId());
        assertThat(detail.title()).isEqualTo("Chiller unit making a noise");
        assertThat(detail.description()).isNull();
        assertThat(detail.slaDueAt()).isNotNull();
        assertThat(detail.assigneeId()).isNull();
        assertThat(detail.totalLaborMinutes()).isZero();

        assertThat(capturedHistory().getToStatus()).isEqualTo(WorkOrderStatus.NEW);
        assertThat(capturedHistory().getFromStatus()).isNull();
        verify(notifications, never()).notifyAssignment(any(), any(), any());
    }

    @Test
    @DisplayName("a dispatcher may raise and assign in one step")
    void createCanAssignImmediately() {
        when(workOrders.nextCodeSequence()).thenReturn(9L);

        WorkOrderDetail detail = service.create(new CreateWorkOrderRequest(
                "Leak in the cold store", null, Priority.HIGH, 10L, tech.getId()), asDispatcher);

        assertThat(detail.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(detail.assigneeId()).isEqualTo(tech.getId());
        // Raised, then assigned: two rows, so the trail shows both facts.
        verify(history, times(2)).save(any(WorkOrderStatusHistory.class));
        verify(notifications).notifyAssignment(any(WorkOrder.class), eq(tech),
                eq(dispatcher.getId()));
    }

    @Test
    @DisplayName("a closed job can no longer be edited")
    void terminalJobsAreFrozen() {
        staged(WorkOrderStatus.CLOSED, tech);

        assertThatThrownBy(() -> service.update(WO_ID, new UpdateWorkOrderRequest(
                "New title", null, Priority.LOW, 10L), asManager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("can no longer be edited");
    }

    @Test
    @DisplayName("a job cannot be re-homed to a different customer's site")
    void cannotRehomeToAnotherCustomer() {
        staged(WorkOrderStatus.NEW, null);
        Customer rival = customer(2L, "Beta Foods");
        when(siteService.loadVisible(any(), any())).thenReturn(site(11L, rival, "Beta Depot"));

        assertThatThrownBy(() -> service.update(WO_ID, new UpdateWorkOrderRequest(
                "Chiller noise", null, Priority.LOW, 11L), asManager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different customer's site");
    }

    @Test
    @DisplayName("raising the priority pulls the deadline in, measured from when it was raised")
    void priorityChangeMovesTheDeadline() {
        WorkOrder wo = staged(WorkOrderStatus.NEW, null);

        service.update(WO_ID, new UpdateWorkOrderRequest(
                "Chiller noise", "Louder now", Priority.URGENT, 10L), asDispatcher);

        assertThat(wo.getPriority()).isEqualTo(Priority.URGENT);
        assertThat(wo.getSlaDueAt()).isEqualTo(wo.getCreatedAt().plus(Duration.ofHours(4)));
        assertThat(wo.getDescription()).isEqualTo("Louder now");
    }

    @Test
    @DisplayName("a technician cannot edit the job they are working on")
    void techniciansCannotEdit() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        assertThatThrownBy(() -> service.update(WO_ID, new UpdateWorkOrderRequest(
                "Renamed by the engineer", null, Priority.LOW, 10L), asTech))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot edit work orders.");
    }

    // ---------------------------------------------------------------- assignment

    @Test
    @DisplayName("only a technician can be assigned work")
    void onlyTechniciansCanBeAssigned() {
        staged(WorkOrderStatus.NEW, null);

        assertThatThrownBy(() -> service.assign(WO_ID,
                new AssignRequest(dispatcher.getId(), null), asManager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is not a technician");
    }

    @Test
    @DisplayName("a deactivated account cannot be assigned work")
    void inactiveAccountsCannotBeAssigned() {
        staged(WorkOrderStatus.NEW, null);
        User retired = user(105L, Role.TECHNICIAN, "Old Hand");
        retired.setActive(false);
        when(users.findById(105L)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> service.assign(WO_ID, new AssignRequest(105L, null), asManager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("account is not active");
    }

    @Test
    @DisplayName("assigning a new job is also its first lifecycle move")
    void assigningANewJobMovesItToAssigned() {
        WorkOrder wo = staged(WorkOrderStatus.NEW, null);

        WorkOrderDetail detail = service.assign(WO_ID,
                new AssignRequest(tech.getId(), "Nearest engineer."), asDispatcher);

        assertThat(detail.status()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(detail.assigneeId()).isEqualTo(tech.getId());

        WorkOrderStatusHistory row = capturedHistory();
        assertThat(row.getFromStatus()).isEqualTo(WorkOrderStatus.NEW);
        assertThat(row.getToStatus()).isEqualTo(WorkOrderStatus.ASSIGNED);
        assertThat(row.getNote()).isEqualTo("Assigned to Sam Okafor. Nearest engineer.");
        verify(notifications).notifyAssignment(wo, tech, dispatcher.getId());
    }

    @Test
    @DisplayName("reassigning mid-job keeps the status and names both engineers")
    void reassigningKeepsTheStatus() {
        WorkOrder wo = staged(WorkOrderStatus.IN_PROGRESS, tech);

        service.assign(WO_ID, new AssignRequest(otherTech.getId(), null), asDispatcher);

        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);
        assertThat(wo.getAssignee()).isSameAs(otherTech);
        assertThat(capturedHistory().getNote())
                .isEqualTo("Reassigned from Sam Okafor to Lena Fischer.");
    }

    @Test
    @DisplayName("reassigning to the engineer who already has it is refused")
    void reassigningToTheSamePersonIsRefused() {
        staged(WorkOrderStatus.ASSIGNED, tech);

        assertThatThrownBy(() -> service.assign(WO_ID,
                new AssignRequest(tech.getId(), null), asDispatcher))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already assigned to Sam Okafor");
    }

    @Test
    @DisplayName("a completed job is not reassigned; it is reopened first")
    void completedJobsCannotBeReassigned() {
        staged(WorkOrderStatus.COMPLETED, tech);

        assertThatThrownBy(() -> service.assign(WO_ID,
                new AssignRequest(otherTech.getId(), null), asDispatcher))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot be assigned");
    }

    @Test
    @DisplayName("technicians cannot hand their work to someone else")
    void techniciansCannotAssign() {
        staged(WorkOrderStatus.ASSIGNED, tech);

        assertThatThrownBy(() -> service.assign(WO_ID,
                new AssignRequest(otherTech.getId(), null), asTech))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You cannot assign work orders.");
    }

    @Test
    @DisplayName("only an ASSIGNED job can go back to the unassigned queue")
    void unassignOnlyFromAssigned() {
        staged(WorkOrderStatus.IN_PROGRESS, tech);

        assertThatThrownBy(() -> service.unassign(WO_ID, null, asDispatcher))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only a work order in ASSIGNED");
    }

    @Test
    @DisplayName("unassigning returns the job to the queue and records who lost it")
    void unassignReturnsTheJobToTheQueue() {
        WorkOrder wo = staged(WorkOrderStatus.ASSIGNED, tech);

        service.unassign(WO_ID, "Engineer off sick.", asDispatcher);

        assertThat(wo.getAssignee()).isNull();
        assertThat(wo.getStatus()).isEqualTo(WorkOrderStatus.NEW);
        assertThat(capturedHistory().getNote())
                .isEqualTo("Unassigned from Sam Okafor. Engineer off sick.");
    }

    @Test
    @DisplayName("an unassigned technician cannot even see the job, let alone claim it")
    void unassignedTechnicianCannotClaimWork() {
        staged(WorkOrderStatus.NEW, null);

        assertThatThrownBy(() -> service.assign(WO_ID,
                new AssignRequest(otherTech.getId(), null), asOtherTech))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ----------------------------------------------------------------- helpers

    /** Put a work order behind the repository so the service can find it. */
    private WorkOrder staged(WorkOrderStatus status, User assignee) {
        WorkOrder wo = workOrder(WO_ID, status, acme, warehouse, assignee);
        when(workOrders.findWithRefsById(WO_ID)).thenReturn(Optional.of(wo));
        return wo;
    }

    private WorkOrderStatusHistory capturedHistory() {
        ArgumentCaptor<WorkOrderStatusHistory> captor =
                ArgumentCaptor.forClass(WorkOrderStatusHistory.class);
        verify(history, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private PartUsage usage(long id, Part forPart, int quantity) {
        PartUsage u = new PartUsage();
        u.setId(id);
        u.setPart(forPart);
        u.setQuantity(quantity);
        u.setUnitCostAtUse(forPart.getUnitCost());
        u.setLoggedBy(tech);
        u.setCreatedAt(NOW);
        return u;
    }

    private TimeLog timeLog(long id, int minutes) {
        TimeLog log = new TimeLog();
        log.setId(id);
        log.setMinutes(minutes);
        log.setTechnician(tech);
        log.setCreatedAt(NOW);
        return log;
    }
}
