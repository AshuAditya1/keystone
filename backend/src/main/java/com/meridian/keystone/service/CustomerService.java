package com.meridian.keystone.service;

import com.meridian.keystone.common.PageableFactory;
import com.meridian.keystone.domain.Customer;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.dto.CustomerRequest;
import com.meridian.keystone.dto.CustomerView;
import com.meridian.keystone.dto.PageResponse;
import com.meridian.keystone.dto.SiteView;
import com.meridian.keystone.exception.BusinessRuleException;
import com.meridian.keystone.exception.ResourceNotFoundException;
import com.meridian.keystone.repository.CustomerRepository;
import com.meridian.keystone.repository.SiteRepository;
import com.meridian.keystone.repository.UserRepository;
import com.meridian.keystone.repository.WorkOrderRepository;
import com.meridian.keystone.security.KeystoneUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("name", "contactEmail", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");

    private final CustomerRepository customers;
    private final SiteRepository sites;
    private final WorkOrderRepository workOrders;
    private final UserRepository users;

    public CustomerService(CustomerRepository customers,
                           SiteRepository sites,
                           WorkOrderRepository workOrders,
                           UserRepository users) {
        this.customers = customers;
        this.sites = sites;
        this.workOrders = workOrders;
        this.users = users;
    }

    public PageResponse<CustomerView> search(String search,
                                             Integer page,
                                             Integer size,
                                             String sort,
                                             KeystoneUserDetails me) {
        Pageable pageable = PageableFactory.of(page, size, sort, SORTABLE_FIELDS, DEFAULT_SORT);
        Specification<Customer> spec = (root, query, cb) -> cb.conjunction();

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("contactEmail")), pattern)));
        }

        // A customer user only ever sees their own organisation in this list.
        if (me.getRole() == Role.CUSTOMER) {
            Long ownId = me.getCustomerId();
            spec = spec.and((root, query, cb) -> ownId == null
                    ? cb.disjunction()
                    : cb.equal(root.get("id"), ownId));
        }

        return PageResponse.from(customers.findAll(spec, pageable), CustomerView::from);
    }

    public CustomerView get(Long id, KeystoneUserDetails me) {
        return CustomerView.from(loadVisible(id, me));
    }

    public List<SiteView> sitesOf(Long customerId, KeystoneUserDetails me) {
        loadVisible(customerId, me);
        return sites.findByCustomerIdOrderByNameAsc(customerId).stream()
                .map(SiteView::from)
                .toList();
    }

    @Transactional
    public CustomerView create(CustomerRequest request) {
        if (customers.existsByNameIgnoreCase(request.name().trim())) {
            throw new BusinessRuleException(
                    "A customer named '" + request.name().trim() + "' already exists.");
        }
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerView.from(customers.save(customer));
    }

    @Transactional
    public CustomerView update(Long id, CustomerRequest request) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        customers.findByNameIgnoreCase(request.name().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BusinessRuleException(
                            "A customer named '" + request.name().trim() + "' already exists.");
                });
        apply(customer, request);
        return CustomerView.from(customers.save(customer));
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        // Refuse rather than cascade — deleting a customer must never silently
        // take their sites, jobs or portal logins with it.
        if (workOrders.existsByCustomerId(id)) {
            throw new BusinessRuleException(
                    "This customer has work orders and cannot be deleted.");
        }
        if (sites.countByCustomerId(id) > 0) {
            throw new BusinessRuleException(
                    "This customer still has sites. Remove the sites first.");
        }
        if (users.existsByCustomerId(id)) {
            throw new BusinessRuleException(
                    "This customer has portal users and cannot be deleted.");
        }
        customers.delete(customer);
    }

    /** Load a customer, enforcing that this caller is allowed to see it. */
    private Customer loadVisible(Long id, KeystoneUserDetails me) {
        Customer customer = customers.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", id));
        if (me.getRole() == Role.CUSTOMER && !id.equals(me.getCustomerId())) {
            throw new AccessDeniedException("You do not have access to this customer.");
        }
        return customer;
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setName(request.name().trim());
        customer.setContactEmail(trimToNull(request.contactEmail()));
        customer.setContactPhone(trimToNull(request.contactPhone()));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
