package com.meridian.keystone.service;

import com.meridian.keystone.common.PageableFactory;
import com.meridian.keystone.domain.Part;
import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.PartRequest;
import com.meridian.keystone.dto.PartView;
import com.meridian.keystone.exception.BusinessRuleException;
import com.meridian.keystone.exception.ResourceNotFoundException;
import com.meridian.keystone.repository.PartRepository;
import com.meridian.keystone.repository.PartUsageRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class PartService {

    /** Stock at or below this level is flagged as running low. */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("sku", "name", "unitCost", "stockQuantity", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "sku");

    private final PartRepository parts;
    private final PartUsageRepository partUsage;

    public PartService(PartRepository parts, PartUsageRepository partUsage) {
        this.parts = parts;
        this.partUsage = partUsage;
    }

    public PageResponse<PartView> search(String search,
                                         Boolean lowStockOnly,
                                         Integer page,
                                         Integer size,
                                         String sort) {
        Pageable pageable = PageableFactory.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        Specification<Part> spec = (root, query, cb) -> cb.conjunction();
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("sku")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern)));
        }
        if (Boolean.TRUE.equals(lowStockOnly)) {
            spec = spec.and((root, query, cb) ->
                    cb.lessThanOrEqualTo(root.get("stockQuantity"), LOW_STOCK_THRESHOLD));
        }
        return PageResponse.from(parts.findAll(spec, pageable), PartView::from);
    }

    /**
     * The whole catalogue, unpaged — used to populate the "log a part" picker in
     * the field view, where a technician needs to scan for a SKU rather than page
     * through it. The inventory is small by design; if it ever were not, this
     * would become a server-side typeahead.
     */
    public List<PartView> catalog() {
        return parts.findAll(DEFAULT_SORT).stream().map(PartView::from).toList();
    }

    public PartView get(Long id) {
        return PartView.from(parts.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Part", id)));
    }

    @Transactional
    public PartView create(PartRequest request) {
        String sku = request.sku().trim();
        if (parts.existsBySkuIgnoreCase(sku)) {
            throw new BusinessRuleException("A part with SKU '" + sku + "' already exists.");
        }
        Part part = new Part();
        part.setSku(sku);
        part.setName(request.name().trim());
        part.setUnitCost(request.unitCost());
        part.setStockQuantity(request.stockQuantity());
        return PartView.from(parts.save(part));
    }

    @Transactional
    public PartView update(Long id, PartRequest request) {
        Part part = parts.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Part", id));
        String sku = request.sku().trim();
        parts.findBySkuIgnoreCase(sku)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "A part with SKU '" + sku + "' already exists.");
                });
        part.setSku(sku);
        part.setName(request.name().trim());
        part.setUnitCost(request.unitCost());
        part.setStockQuantity(request.stockQuantity());
        return PartView.from(parts.save(part));
    }

    @Transactional
    public void delete(Long id) {
        Part part = parts.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Part", id));
        // Parts already consumed are referenced by the audit trail.
        if (partUsage.existsByPartId(id)) {
            throw new BusinessRuleException(
                    "This part has been used on work orders and cannot be deleted.");
        }
        parts.delete(part);
    }
}
