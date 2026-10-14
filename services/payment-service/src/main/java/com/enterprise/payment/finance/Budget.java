package com.enterprise.payment.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

/** Monthly spending limit of a seller for one expense category. */
@Entity
@Table(name = "budgets", uniqueConstraints = @UniqueConstraint(name = "uk_budgets_seller_month_category",
        columnNames = {"seller_id", "budget_month", "category"}))
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** ISO year-month, e.g. 2026-10. */
    @Column(name = "budget_month", nullable = false, length = 7)
    private String budgetMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(name = "limit_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version")
    private Long version;

    protected Budget() {
    }

    public Budget(Long sellerId, String budgetMonth, ExpenseCategory category, BigDecimal amount) {
        this.sellerId = sellerId;
        this.budgetMonth = budgetMonth;
        this.category = category;
        this.amount = amount;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public String getBudgetMonth() {
        return budgetMonth;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
