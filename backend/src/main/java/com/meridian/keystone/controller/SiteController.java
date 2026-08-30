package com.meridian.keystone.controller;

import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.SiteRequest;
import com.meridian.keystone.dto.SiteView;
import com.meridian.keystone.security.KeystoneUserDetails;
import com.meridian.keystone.service.SiteService;
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

/**
 * Sites — the physical locations work is carried out at.
 *
 * <p>A site always belongs to exactly one customer, and that link is what scopes
 * a portal user's view of the system, so the service re-filters every read by
 * the caller's own customer regardless of the {@code customerId} they ask for.
 */
@RestController
@RequestMapping("/api/sites")
@Tag(name = "Sites")
public class SiteController {

    private final SiteService sites;

    public SiteController(SiteService sites) {
        this.sites = sites;
    }

    @GetMapping
    @Operation(summary = "List sites",
            description = "Optionally narrowed to one customer, and searchable by name or "
                    + "address. Portal users only ever see their own sites.")
    public PageResponse<SiteView> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal KeystoneUserDetails me) {
        return sites.search(customerId, search, page, size, sort, me);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Site detail")
    public SiteView get(@PathVariable Long id,
                        @AuthenticationPrincipal KeystoneUserDetails me) {
        return sites.get(id, me);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Create a site")
    public ResponseEntity<SiteView> create(@Valid @RequestBody SiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sites.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER')")
    @Operation(summary = "Update a site",
            description = "Moving a site to a different customer is refused with 409 once it "
                    + "has work orders, because that would re-home their history.")
    public SiteView update(@PathVariable Long id,
                           @Valid @RequestBody SiteRequest request) {
        return sites.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a site",
            description = "Refused with 409 if any work order references it.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sites.delete(id);
        return ResponseEntity.noContent().build();
    }
}
