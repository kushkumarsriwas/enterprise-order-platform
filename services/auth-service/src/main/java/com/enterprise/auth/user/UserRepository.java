package com.enterprise.auth.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findFirstByRole(Role role);

    long countByRole(Role role);

    long countByRoleAndEnabledFalse(Role role);

    /** Sign-ups per day and role since a date (admin dashboard): day, role, count. */
    @Query("""
            select cast(u.createdAt as LocalDate), u.role, count(u) from User u
            where u.createdAt >= :since and u.role <> com.enterprise.auth.user.Role.ADMIN
            group by cast(u.createdAt as LocalDate), u.role
            order by cast(u.createdAt as LocalDate)""")
    List<Object[]> signupsPerDay(Instant since);
}
