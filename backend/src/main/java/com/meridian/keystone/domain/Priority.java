package com.meridian.keystone.domain;

/**
 * Work-order priority. Drives the SLA due date (see SLA logic in M3).
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}
