package com.enterprise.payment.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A business expense of a seller (rent, salaries, marketing...). */
@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expenses_seller_date", columnList = "seller_id, expense_date"))
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 100)
    private String vendor;

    /** Invoice or receipt number. */
    @Column(name = "reference_no", length = 60)
    private String reference;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version")
    private Long version;

    protected Expense() {
    }

    public Expense(Long sellerId, ExpenseCategory category, BigDecimal amount, String currency, String description,
                   String vendor, String reference, LocalDate expenseDate, String createdBy) {
        this.sellerId = sellerId;
        this.category = category;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.vendor = vendor;
        this.reference = reference;
        this.expenseDate = expenseDate;
        this.createdBy = createdBy;
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

    public void update(ExpenseCategory category, BigDecimal amount, String description, String vendor,
                       String reference, LocalDate expenseDate) {
        this.category = category;
        this.amount = amount;
        this.description = description;
        this.vendor = vendor;
        this.reference = reference;
        this.expenseDate = expenseDate;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public String getVendor() {
        return vendor;
    }

    public String getReference() {
        return reference;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
