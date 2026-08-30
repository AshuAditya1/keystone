package com.meridian.keystone.domain;

/**
 * Kinds of in-app notification. Kept in sync with the CHECK constraint on
 * notifications.type (see V3__sla_notifications.sql).
 */
public enum NotificationType {
    SLA_BREACH,
    SLA_AT_RISK,
    WORK_ORDER_ASSIGNED,
    STATUS_CHANGED
}
