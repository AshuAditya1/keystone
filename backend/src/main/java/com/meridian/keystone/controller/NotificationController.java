package com.meridian.keystone.controller;

import com.meridian.keystone.dto.CountResponse;
import com.meridian.keystone.dto.NotificationView;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-app notifications.
 *
 * <p>No role checks are needed here, and none would help: every method is scoped
 * to the authenticated principal's own inbox by the service. There is no
 * parameter through which one user could ask for another's notifications — the
 * recipient id always comes from the token, never the request.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "My recent notifications",
            description = "Newest first, capped — this feeds a dropdown, not an archive.")
    public List<NotificationView> recent(@AuthenticationPrincipal KeystoneUserDetails me) {
        return notifications.recent(me);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "My unread count",
            description = "Cheap enough to poll for the badge on the bell.")
    public CountResponse unreadCount(@AuthenticationPrincipal KeystoneUserDetails me) {
        return CountResponse.of(notifications.unreadCount(me));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one as read",
            description = "404 rather than 403 if the notification belongs to someone else — "
                    + "an inbox should not confirm what it does not contain.")
    public NotificationView markRead(@PathVariable Long id,
                                     @AuthenticationPrincipal KeystoneUserDetails me) {
        return notifications.markRead(id, me);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all as read",
            description = "Returns how many were cleared.")
    public CountResponse markAllRead(@AuthenticationPrincipal KeystoneUserDetails me) {
        return CountResponse.of(notifications.markAllRead(me));
    }
}
