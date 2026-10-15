package com.enterprise.inventory.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Marketplace department ("Electronics", "Home & Kitchen", ...). */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    /** Icon name used by the storefront (lucide icon set). */
    @Column(nullable = false, length = 40)
    private String icon = "tag";

    protected Category() {
    }

    public Category(String name, String description, String icon) {
        this.name = name;
        this.description = description;
        this.icon = icon == null || icon.isBlank() ? "tag" : icon;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }
}
