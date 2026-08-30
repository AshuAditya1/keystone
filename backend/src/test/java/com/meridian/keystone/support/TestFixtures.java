package com.meridian.keystone.support;

import com.meridian.keystone.domain.Customer;
import com.meridian.keystone.domain.Part;
import com.meridian.keystone.domain.Priority;
import com.meridian.keystone.domain.Role;
import com.meridian.keystone.domain.Site;
import com.meridian.keystone.domain.SlaStatus;
import com.meridian.keystone.domain.User;
import com.meridian.keystone.domain.WorkOrder;
import com.meridian.keystone.domain.WorkOrderStatus;
import com.meridian.keystone.security.KeystoneUserDetails;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Object builders for the unit tests.
 *
 * <p>These tests deliberately run without a database or a Spring context: the
 * rules being verified — the lifecycle, the SLA policy, the authorization gates —
 * are pure logic, and testing them through a container would only make the
 * failures harder to read. That means entities are hand-built here, including
 * their ids, which JPA would normally assign.
 */
public final class TestFixtures {

    public static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");

    private TestFixtures() {
    }

    public static Customer customer(long id, String name) {
        Customer c = new Customer();
        c.setId(id);
        c.setName(name);
        return c;
    }

    public static Site site(long id, Customer customer, String name) {
        Site s = new Site();
        s.setId(id);
        s.setCustomer(customer);
        s.setName(name);
        s.setAddress(name + ", London");
        return s;
    }

    public static User user(long id, Role role, String fullName) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        u.setFullName(fullName);
        u.setEmail(fullName.toLowerCase().replace(' ', '.') + "@meridian.test");
        u.setPasswordHash("{noop}irrelevant");
        u.setActive(true);
        return u;
    }

    public static User portalUser(long id, String fullName, Customer customer) {
        User u = user(id, Role.CUSTOMER, fullName);
        u.setCustomer(customer);
        return u;
    }

    public static KeystoneUserDetails principal(User user) {
        return new KeystoneUserDetails(user);
    }

    public static Part part(long id, String sku, String cost, int stock) {
        Part p = new Part();
        p.setId(id);
        p.setSku(sku);
        p.setName(sku + " widget");
        p.setUnitCost(new BigDecimal(cost));
        p.setStockQuantity(stock);
        return p;
    }

    /** A work order in a given state, wired to a customer, site and (maybe) assignee. */
    public static WorkOrder workOrder(long id,
                                      WorkOrderStatus status,
                                      Customer customer,
                                      Site site,
                                      User assignee) {
        WorkOrder wo = new WorkOrder();
        wo.setId(id);
        wo.setCode("WO-2026-" + String.format("%04d", id));
        wo.setTitle("Chiller unit making a noise");
        wo.setPriority(Priority.MEDIUM);
        wo.setStatus(status);
        wo.setCustomer(customer);
        wo.setSite(site);
        wo.setAssignee(assignee);
        wo.setCreatedAt(NOW.minusSeconds(3600));
        wo.setSlaDueAt(NOW.plusSeconds(3600));
        wo.setSlaStatus(SlaStatus.ON_TRACK);
        wo.setTotalLaborMinutes(0);
        wo.setTotalPartsCost(BigDecimal.ZERO);
        return wo;
    }
}
