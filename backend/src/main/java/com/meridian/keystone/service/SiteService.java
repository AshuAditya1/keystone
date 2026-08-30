package com.meridian.keystone.service;

import com.meridian.keystone.common.PageableFactory;
import com.meridian.keystone.domain.Customer;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.Site;
import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.SiteRequest;
import com.meridian.keystone.dto.SiteView;
import com.meridian.keystone.exception.BusinessRuleException;
import com.meridian.keystone.exception.ResourceNotFoundException;
import com.meridian.keystone.repository.CustomerRepository;
import com.meridian.keystone.repository.SiteRepository;
import com.meridian.keystone.repository.WorkOrderRepository;
import com.meridian.keystone.security.KeystoneUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional(readOnly = true)
public class SiteService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "address", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final SiteRepository sites;
    private final CustomerRepository customers;
    private final WorkOrderRepository workOrders;

    public SiteService(SiteRepository sites,
                       CustomerRepository customers,
                       WorkOrderRepository workOrders) {
        this.sites = sites;
        this.customers = customers;
        this.workOrders = workOrders;
    }

    public PageResponse<SiteView> search(Long customerId,
                                         String search,
                                         Integer page,
                                         Integer size,
                                         String sort,
                                         KeystoneUserDetails me) {
        Pageable pageable = PageableFactory.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        Specification<Site> spec = (root, query, cb) -> cb.conjunction();

        if (customerId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("customer").get("id"), customerId));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern)));
        }
        // Customers only see their own sites, whatever they asked for.
        if (me.getRole() == Role.CUSTOMER) {
            Long ownId = me.getCustomerId();
            spec = spec.and((root, query, cb) -> ownId == null
                    ? cb.disjunction()
                    : cb.equal(root.get("customer").get("id"), ownId));
        }

        return PageResponse.from(sites.findAll(spec, pageable), SiteView::from);
    }

    public SiteView get(Long id, KeystoneUserDetails me) {
        return SiteView.from(loadVisible(id, me));
    }

    @Transactional
    public SiteView create(SiteRequest request) {
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));
        if (sites.existsByCustomerIdAndNameIgnoreCase(customer.getId(), request.name().trim())) {
            throw new BusinessRuleException(
                    "This customer already has a site named '" + request.name().trim() + "'.");
        }
        Site site = new Site();
        site.setCustomer(customer);
        site.setName(request.name().trim());
        site.setAddress(trimToNull(request.address()));
        return SiteView.from(sites.save(site));
    }

    @Transactional
    public SiteView update(Long id, SiteRequest request) {
        Site site = sites.findWithCustomerById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", id));
        Customer customer = customers.findById(request.customerId())
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", request.customerId()));

        // Moving a site between customers would silently re-home its existing
        // work orders, so it is refused once any exist.
        if (!site.getCustomer().getId().equals(customer.getId())
                && workOrders.existsBySiteId(id)) {
            throw new BusinessRuleException(
                    "This site has work orders, so it cannot be moved to another customer.");
        }
        site.setCustomer(customer);
        site.setName(request.name().trim());
        site.setAddress(trimToNull(request.address()));
        return SiteView.from(sites.save(site));
    }

    @Transactional
    public void delete(Long id) {
        Site site = sites.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", id));
        if (workOrders.existsBySiteId(id)) {
            throw new BusinessRuleException(
                    "This site has work orders and cannot be deleted.");
        }
        sites.delete(site);
    }

    /**
     * Load a site for a caller who may only touch their own — used both by the
     * read endpoint and when a customer raises a request against a site.
     */
    public Site loadVisible(Long id, KeystoneUserDetails me) {
        Site site = sites.findWithCustomerById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Site", id));
        if (me.getRole() == Role.CUSTOMER
                && !site.getCustomer().getId().equals(me.getCustomerId())) {
            throw new AccessDeniedException("You do not have access to this site.");
        }
        return site;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
