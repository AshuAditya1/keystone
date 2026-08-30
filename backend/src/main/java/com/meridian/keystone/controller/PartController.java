package com.meridian.keystone.controller;

import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.PartRequest;
import com.meridian.keystone.dto.PartView;
import com.meridian.keystone.service.PartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Parts inventory.
 *
 * <p>Unlike customers and sites, this is internal data: unit costs are Meridian's
 * margin, not the customer's business, so the whole controller is closed to the
 * {@code CUSTOMER} role. Technicians may read the catalogue (they need it to log
 * what they fitted) but only managers may change stock or pricing.
 */
@RestController
@RequestMapping("/api/parts")
@Tag(name = "Parts")
@PreAuthorize("hasAnyRole('MANAGER', 'DISPATCHER', 'TECHNICIAN')")
public class PartController {

    private final PartService parts;

    public PartController(PartService parts) {
        this.parts = parts;
    }

    @GetMapping
    @Operation(summary = "List parts",
            description = "Paged, searchable by SKU or name, and optionally narrowed to items "
                    + "running low on stock.")
    public PageResponse<PartView> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean lowStockOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return parts.search(search, lowStockOnly, page, size, sort);
    }

    @GetMapping("/catalog")
    @Operation(summary = "Whole catalogue, unpaged",
            description = "Feeds the part picker in the field view, where a technician scans "
                    + "for a SKU rather than paging through the inventory.")
    public List<PartView> catalog() {
        return parts.catalog();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Part detail")
    public PartView get(@PathVariable Long id) {
        return parts.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Add a part",
            description = "SKUs are unique, case-insensitively; a duplicate is a 409.")
    public ResponseEntity<PartView> create(@Valid @RequestBody PartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parts.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Update a part",
            description = "Changing the unit cost does not rewrite history: every parts line "
                    + "already logged keeps the price that was in force when it was used.")
    public PartView update(@PathVariable Long id,
                           @Valid @RequestBody PartRequest request) {
        return parts.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Delete a part",
            description = "Refused with 409 once the part appears on any work order.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        parts.delete(id);
        return ResponseEntity.noContent().build();
    }
}
