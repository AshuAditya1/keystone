package com.meridian.keystone.dto;

/**
 * A single number, wrapped in an object.
 *
 * <p>Returning a bare {@code 7} as a response body works, but it cannot grow — a
 * client that parses a number has to be rewritten the day the endpoint needs to
 * return anything alongside it. {@code {"count": 7}} costs nothing now and stays
 * extensible.
 */
public record CountResponse(long count) {

    public static CountResponse of(long count) {
        return new CountResponse(count);
    }
}
