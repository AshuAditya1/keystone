package com.meridian.keystone.service;

import com.meridian.keystone.domain.Customer;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.User;
import com.meridian.keystone.dto.CreateUserRequest;
import com.meridian.keystone.dto.UpdateUserRequest;
import com.meridian.keystone.dto.UserSummary;
import com.meridian.keystone.exception.BusinessRuleException;
import com.meridian.keystone.exception.ResourceNotFoundException;
import com.meridian.keystone.repository.CustomerRepository;
import com.meridian.keystone.repository.UserRepository;
import com.meridian.keystone.repository.WorkOrderRepository;
import com.meridian.keystone.security.KeystoneUserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User administration (manager-only at the controller) plus the technician
 * lookup that dispatchers need to assign work.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;
    private final CustomerRepository customers;
    private final WorkOrderRepository workOrders;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users,
                       CustomerRepository customers,
                       WorkOrderRepository workOrders,
                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.customers = customers;
        this.workOrders = workOrders;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummary> list(Role role) {
        List<User> found = role == null
                ? users.findAllByOrderByFullNameAsc()
                : users.findByRoleOrderByFullNameAsc(role);
        return found.stream().map(UserSummary::from).toList();
    }

    /** Candidates for assignment: active technicians, name-ordered. */
    public List<UserSummary> assignableTechnicians() {
        return users.findByRoleOrderByFullNameAsc(Role.TECHNICIAN).stream()
                .filter(User::isActive)
                .map(UserSummary::from)
                .toList();
    }

    public UserSummary get(Long id) {
        return UserSummary.from(users.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id)));
    }

    @Transactional
    public UserSummary create(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("A user with email '" + email + "' already exists.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        user.setActive(true);
        user.setCustomer(resolveCustomer(request.role(), request.customerId()));
        return UserSummary.from(users.save(user));
    }

    @Transactional
    public UserSummary update(Long id, UpdateUserRequest request, KeystoneUserDetails me) {
        User user = users.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("User", id));

        boolean deactivating = Boolean.FALSE.equals(request.active());
        boolean losingTechnicianRole = request.role() != Role.TECHNICIAN
                && user.getRole() == Role.TECHNICIAN;

        // A manager locking themselves out is a support ticket, not a feature.
        if (user.getId().equals(me.getId())) {
            if (deactivating) {
                throw new BusinessRuleException("You cannot deactivate your own account.");
            }
            if (request.role() != user.getRole()) {
                throw new BusinessRuleException("You cannot change your own role.");
            }
        }
        // Work would become unreachable through the technician's own views.
        if ((deactivating || losingTechnicianRole) && workOrders.existsByAssigneeId(id)) {
            throw new BusinessRuleException(
                    "This technician still has assigned work orders. Reassign them first.");
        }

        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        if (request.active() != null) {
            user.setActive(request.active());
        }
        user.setCustomer(resolveCustomer(request.role(), request.customerId()));
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return UserSummary.from(users.save(user));
    }

    /**
     * Portal users must belong to exactly one customer; staff must belong to
     * none. Enforcing it here keeps the data-scoping rule in
     * {@code WorkOrderAccess} meaningful — a CUSTOMER row without a customer id
     * would silently see nothing.
     */
    private Customer resolveCustomer(Role role, Long customerId) {
        if (role != Role.CUSTOMER) {
            return null;
        }
        if (customerId == null) {
            throw new BusinessRuleException(
                    "A customer-portal user must be linked to a customer.");
        }
        return customers.findById(customerId)
                .orElseThrow(() -> ResourceNotFoundException.of("Customer", customerId));
    }
}
