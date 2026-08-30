package com.meridian.keystone.auth;

import com.meridian.keystone.dto.LoginRequest;
import com.meridian.keystone.dto.LoginResponse;
import com.meridian.keystone.dto.UserView;
import com.meridian.keystone.security.KeystoneUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 * <ul>
 *   <li>{@code POST /api/auth/login} — public; exchanges credentials for a JWT.</li>
 *   <li>{@code GET  /api/auth/me}    — returns the current authenticated user.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // public: no bearer token needed
    @Operation(summary = "Log in", description = "Exchange email + password for a JWT and user profile.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Returns the profile of the authenticated caller.")
    public ResponseEntity<UserView> me(@AuthenticationPrincipal KeystoneUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(UserView.from(principal.getUser()));
    }
}
