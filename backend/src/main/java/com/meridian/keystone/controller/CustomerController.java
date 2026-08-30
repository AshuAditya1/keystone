package com.meridian.keystone.controller;

import com.meridian.keystone.dto.CustomerRequest;
import com.meridian.keystone.dto.CustomerView;
import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.SiteView;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Customer accounts.
 *
 * <p>Reads are open to any authenticated user because names are needed all over
 * the app, but the service intersects every read with the caller's scope — a
 * portal user asking for the list gets exactly one row, their own. Writes are
 * managers only.
 */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @GetMapping
    @Operation(summary = "List customers",
            description = "Paged and searchable by name or contact email. A customer-portal "
                    + "user sees only their own organisation.")
    public PageResponse<CustomerView> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal KeystoneUserDetails me) {
        return customers.search(search, page, size, sort, me);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Customer detail")
    public CustomerView get(@PathVariable Long id,
                            @AuthenticationPrincipal KeystoneUserDetails me) {
        return customers.get(id, me);
    }

    @GetMapping("/{id}/sites")
    @Operation(summary = "Sites belonging to a customer",
            description = "Unpaged — used to populate the site picker when raising a job.")
    public List<SiteView> sites(@PathVariable Long id,
                                @AuthenticationPrincipal KeystoneUserDetails me) {
        return customers.sitesOf(id, me);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Create a customer")
    public ResponseEntity<CustomerView> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customers.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a customer")
    public CustomerView update(@PathVariable Long id,
                               @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a customer",
            description = "Refused with 409 if the customer still has sites, work orders or "
                    + "portal users — history is never silently cascaded away.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        customers.delete(id);
        return ResponseEntity.noContent().build();
    }
}
