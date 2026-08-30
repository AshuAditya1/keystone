package com.meridian.keystone.domain;

/**
 * SLA state derived from the due date and current time (computed in M3).
 */
public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    BREACHED
}
