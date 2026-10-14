package com.enterprise.inventory.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Next number of one document type for one seller. Read and incremented under a row lock. */
@Entity
@Table(name = "document_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_sequence", columnNames = {"seller_id", "doc_type"}))
public class DocumentSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 20)
    private DocumentType docType;

    @Column(name = "next_value", nullable = false)
    private long nextValue = 1;

    protected DocumentSequence() {
    }

    public DocumentSequence(Long sellerId, DocumentType docType) {
        this.sellerId = sellerId;
        this.docType = docType;
    }

    long take() {
        return nextValue++;
    }
}
