package com.enterprise.inventory.numbering;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, Long> {

    /** SELECT ... FOR UPDATE: two requests can never get the same number. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DocumentSequence s where s.sellerId = :sellerId and s.docType = :type")
    Optional<DocumentSequence> lockFor(Long sellerId, DocumentType type);
}
