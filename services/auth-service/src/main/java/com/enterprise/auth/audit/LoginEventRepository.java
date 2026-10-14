package com.enterprise.auth.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LoginEventRepository extends JpaRepository<LoginEvent, Long>, JpaSpecificationExecutor<LoginEvent> {

    List<LoginEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Limit limit);

    long countBySuccessFalseAndCreatedAtAfter(Instant since);

    long countByUserIdAndSuccessFalseAndCreatedAtAfter(Long userId, Instant since);

    long countBySuccessTrueAndCreatedAtAfter(Instant since);

    @Query("select count(distinct e.ipAddress) from LoginEvent e where e.success = false and e.createdAt > :since")
    long distinctFailingIps(Instant since);
}
