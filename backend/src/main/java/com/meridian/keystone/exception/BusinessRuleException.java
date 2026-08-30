package com.meridian.keystone.exception;

/**
 * Thrown when a request is well-formed but violates a business rule
 * (e.g. an illegal work-order status transition, or insufficient stock).
 * Mapped to HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
