package com.enterprise.auth.audit;

import com.enterprise.auth.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the login audit trail in its own transaction (REQUIRES_NEW): a failed sign-in rolls back the login
 * transaction, but the failed attempt must still be recorded.
 */
@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    private final LoginEventRepository repository;

    public LoginAuditService(LoginEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String username, Role portal, boolean success, String failureReason,
                       ClientInfo client) {
        repository.save(new LoginEvent(userId, username.length() > 120 ? username.substring(0, 120) : username,
                portal, success, failureReason, client));
        if (!success) {
            log.info("Failed {} sign-in for '{}' from {}: {}", portal, username, client.ipAddress(), failureReason);
        }
    }
}
