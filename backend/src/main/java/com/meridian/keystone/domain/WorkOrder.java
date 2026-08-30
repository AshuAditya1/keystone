package com.meridian.keystone.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A unit of maintenance work — the heart of the system. Always belongs to a
 * customer and a site; optionally assigned to a technician. Its status moves
 * through the governed {@link WorkOrderStatus} lifecycle, and every change is
 * recorded in {@link WorkOrderStatusHistory}.
 */
@Entity
@Table(name = "work_orders")
public class WorkOrder extends BaseEntity {

    /** Human-readable unique code, e.g. WO-2026-0001. */
    @Column(name = "code", nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkOrderStatus status = WorkOrderStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    /** The assigned technician, if any. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "sla_due_at")
    private Instant slaDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sla_status", nullable = false, length = 20)
    private SlaStatus slaStatus = SlaStatus.ON_TRACK;

    /** Set when the job reaches COMPLETED; freezes the SLA outcome. */
    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_labor_minutes", nullable = false)
    private int totalLaborMinutes = 0;

    @Column(name = "total_parts_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPartsCost = BigDecimal.ZERO;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public void setStatus(WorkOrderStatus status) {
        this.status = status;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public User getAssignee() {
        return assignee;
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(Instant slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public SlaStatus getSlaStatus() {
        return slaStatus;
    }

    public void setSlaStatus(SlaStatus slaStatus) {
        this.slaStatus = slaStatus;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getTotalLaborMinutes() {
        return totalLaborMinutes;
    }

    public void setTotalLaborMinutes(int totalLaborMinutes) {
        this.totalLaborMinutes = totalLaborMinutes;
    }

    public BigDecimal getTotalPartsCost() {
        return totalPartsCost;
    }

    public void setTotalPartsCost(BigDecimal totalPartsCost) {
        this.totalPartsCost = totalPartsCost;
    }
}
