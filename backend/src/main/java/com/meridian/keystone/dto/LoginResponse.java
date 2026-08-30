package com.meridian.keystone.dto;

/**
 * Returned by {@code POST /api/auth/login}. The frontend stores the token and
 * uses {@code user} to drive role-aware rendering.
 */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMinutes,
        UserView user) {

    public static LoginResponse of(String token, long expiresInMinutes, UserView user) {
        return new LoginResponse(token, "Bearer", expiresInMinutes, user);
    }
}
