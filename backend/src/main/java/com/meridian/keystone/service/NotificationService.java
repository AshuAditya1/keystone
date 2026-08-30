package com.meridian.keystone.service;

import com.meridian.keystone.domain.Notification;
import com.meridian.keystone.domain.NotificationType;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.dto.NotificationView;
import com.meridian.keystone.exception.ResourceNotFoundException;
import com.meridian.keystone.repository.NotificationRepository;
import com.meridian.keystone.repository.UserRepository;
import com.meridian.keystone.security.KeystoneUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * In-app notifications. Created inside the same transaction as the change that
 * caused them, so a rolled-back status change never leaves a stray alert.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final List<Role> OPS_ROLES = List.of(Role.MANAGER, Role.DISPATCHER);

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    public List<NotificationView> recent(KeystoneUserDetails me) {
        return notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(NotificationView::from)
                .toList();
    }

    public long unreadCount(KeystoneUserDetails me) {
        return notifications.countByRecipientIdAndReadAtIsNull(me.getId());
    }

    @Transactional
    public NotificationView markRead(Long id, KeystoneUserDetails me) {
        // Scoped by recipient, so one user can never mark another's alerts read.
        Notification notification = notifications.findByIdAndRecipientId(id, me.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notifications.save(notification);
        }
        return NotificationView.from(notification);
    }

    @Transactional
    public int markAllRead(KeystoneUserDetails me) {
        return notifications.markAllRead(me.getId(), Instant.now());
    }

    /** Tell a technician they have new work. */
    @Transactional
    public void notifyAssignment(WorkOrder wo, User assignee, Long actorId) {
        if (assignee == null || assignee.getId().equals(actorId)) {
            return;
        }
        save(assignee, wo, NotificationType.WORK_ORDER_ASSIGNED,
                "Assigned: " + wo.getCode(),
                wo.getTitle() + " at " + wo.getSite().getName()
                        + " (" + wo.getPriority() + " priority).");
    }

    /**
     * Announce a lifecycle move. The assignee hears about changes made by other
     * people; operations hear when a job finishes or is cancelled.
     */
    @Transactional
    public void notifyStatusChange(WorkOrder wo,
                                   WorkOrderStatus from,
                                   WorkOrderStatus to,
                                   Long actorId) {
        String title = wo.getCode() + " → " + to;
        String message = "Moved from " + from + " to " + to + ".";

        User assignee = wo.getAssignee();
        if (assignee != null && !assignee.getId().equals(actorId)) {
            save(assignee, wo, NotificationType.STATUS_CHANGED, title, message);
        }
        if (to == WorkOrderStatus.COMPLETED || to == WorkOrderStatus.CANCELLED) {
            for (User staff : users.findByRoleInAndActiveTrue(OPS_ROLES)) {
                if (!staff.getId().equals(actorId)) {
                    save(staff, wo, NotificationType.STATUS_CHANGED, title, message);
                }
            }
        }
    }

    /**
     * Raise an SLA alert at most once per work order, type and recipient — the
     * sweep runs every minute and must not spam.
     */
    @Transactional
    public void notifySlaOnce(WorkOrder wo, NotificationType type) {
        String title = type == NotificationType.SLA_BREACH
                ? "SLA breached: " + wo.getCode()
                : "SLA at risk: " + wo.getCode();
        String message = type == NotificationType.SLA_BREACH
                ? wo.getTitle() + " passed its deadline while still " + wo.getStatus() + "."
                : wo.getTitle() + " is approaching its deadline and is still " + wo.getStatus() + ".";

        for (User recipient : slaRecipients(wo)) {
            if (!notifications.existsByWorkOrderIdAndTypeAndRecipientId(
                    wo.getId(), type, recipient.getId())) {
                save(recipient, wo, type, title, message);
            }
        }
    }

    private List<User> slaRecipients(WorkOrder wo) {
        List<User> recipients = new ArrayList<>(
                users.findByRoleInAndActiveTrue(OPS_ROLES));
        User assignee = wo.getAssignee();
        if (assignee != null && assignee.isActive()
                && recipients.stream().noneMatch(u -> u.getId().equals(assignee.getId()))) {
            recipients.add(assignee);
        }
        return recipients;
    }

    private void save(User recipient,
                      WorkOrder wo,
                      NotificationType type,
                      String title,
                      String message) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setWorkOrder(wo);
        notification.setType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notifications.save(notification);
    }
}
