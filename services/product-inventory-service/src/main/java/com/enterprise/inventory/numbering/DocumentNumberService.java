package com.enterprise.inventory.numbering;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gap-free document numbers per seller, like invoice numbers in accounting: the counter row is locked and
 * incremented inside the caller's transaction, so if the business transaction rolls back the number is
 * not used up either.
 */
@Service
public class DocumentNumberService {

    private final DocumentSequenceRepository repository;
    private final TransactionTemplate newTransaction;

    public DocumentNumberService(DocumentSequenceRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Long sellerId, DocumentType type) {
        DocumentSequence sequence = repository.lockFor(sellerId, type).orElse(null);
        if (sequence == null) {
            createCounter(sellerId, type);
            sequence = repository.lockFor(sellerId, type).orElseThrow();
        }
        return type.prefix() + "-" + String.format("%05d", sequence.take());
    }

    /** First document of this type: create the counter in its own small transaction (another request may race). */
    private void createCounter(Long sellerId, DocumentType type) {
        try {
            newTransaction.executeWithoutResult(status -> repository.saveAndFlush(new DocumentSequence(sellerId, type)));
        } catch (DataIntegrityViolationException alreadyCreated) {
            // created concurrently by another request - fine, we lock it next
        }
    }
}
