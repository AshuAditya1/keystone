package com.meridian.keystone.dto;

import com.meridian.keystone.domain.Notification;
import com.meridian.keystone.domain.NotificationType;

import java.time.Instant;

public record NotificationView(
        Long id,
        NotificationType type,
        String title,
        String message,
        Long workOrderId,
        String workOrderCode,
        boolean read,
        Instant createdAt) {

    public static NotificationView from(Notification n) {
        return new NotificationView(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getWorkOrder() == null ? null : n.getWorkOrder().getId(),
                n.getWorkOrder() == null ? null : n.getWorkOrder().getCode(),
                n.getReadAt() != null,
                n.getCreatedAt());
    }
}
