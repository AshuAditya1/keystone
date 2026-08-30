package com.meridian.keystone.controller;

import com.meridian.keystone.domain.Role;
import com.meridian.keystone.dto.CreateUserRequest;
import com.meridian.keystone.dto.UpdateUserRequest;
import com.meridian.keystone.dto.UserSummary;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User administration.
 *
 * <p>Managers only, with one deliberate exception: dispatchers may read the list
 * of assignable technicians, because they cannot do their job without it. That
 * endpoint returns names and ids and nothing else — it is not a way to enumerate
 * accounts.
 *
 * <p>There is no delete. Accounts are deactivated instead, so that the audit
 * trail keeps pointing at a real person years after they leave.
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
@PreAuthorize("hasRole('MANAGER')")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List users",
            description = "Optionally filtered to one role. Includes inactive accounts, "
                    + "flagged as such.")
    public List<UserSummary> list(@RequestParam(required = false) Role role) {
        return users.list(role);
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Assignable technicians",
            description = "Active technicians only — the source for the assignment picker.")
    public List<UserSummary> technicians() {
        return users.assignableTechnicians();
    }

    @GetMapping("/{id}")
    @Operation(summary = "User detail")
    public UserSummary get(@PathVariable Long id) {
        return users.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a user",
            description = "A CUSTOMER-role account must name the customer it belongs to; that "
                    + "link is what scopes everything they can subsequently see.")
    public ResponseEntity<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(users.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user",
            description = "Name, role, active flag, customer link and an optional password "
                    + "reset. A manager cannot demote or deactivate their own account, and a "
                    + "technician holding open jobs cannot be deactivated or re-roled until "
                    + "that work is handed over.")
    public UserSummary update(@PathVariable Long id,
                              @Valid @RequestBody UpdateUserRequest request,
                              @AuthenticationPrincipal KeystoneUserDetails me) {
        return users.update(id, request, me);
    }
}
