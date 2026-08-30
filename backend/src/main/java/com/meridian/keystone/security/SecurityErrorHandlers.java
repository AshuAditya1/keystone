package com.meridian.keystone.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.keystone.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Renders authentication (401) and authorization (403) failures using the same
 * {@link ApiError} JSON shape as the rest of the API, instead of Spring
 * Security's default HTML pages.
 */
@Component
public class SecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public SecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 401 — no or invalid credentials on a protected endpoint. */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException authException) ->
                write(response, request, HttpStatus.UNAUTHORIZED,
                        "Authentication required");
    }

    /** 403 — authenticated, but lacking the required role. */
    public AccessDeniedHandler accessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                AccessDeniedException accessDeniedException) ->
                write(response, request, HttpStatus.FORBIDDEN,
                        "You do not have permission to perform this action");
    }

    private void write(HttpServletResponse response, HttpServletRequest request,
                       HttpStatus status, String message) throws IOException {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(),
                message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
