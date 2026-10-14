package com.enterprise.inventory.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Photo uploaded by the seller (one per product, max 2 MB). Kept in the database (bytea) to keep the
 * deployment simple; at scale it would live in object storage (S3) behind a CDN.
 */
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "content_type", nullable = false, length = 30)
    private String contentType;

    /** bytea on PostgreSQL (H2 understands the same type name in the tests). */
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductImage() {
    }

    public ProductImage(Long productId) {
        this.productId = productId;
    }

    public void replace(String contentType, byte[] data) {
        this.contentType = contentType;
        this.data = data;
        this.sizeBytes = data.length;
        this.updatedAt = Instant.now();
    }

    public Long getProductId() {
        return productId;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getData() {
        return data;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
