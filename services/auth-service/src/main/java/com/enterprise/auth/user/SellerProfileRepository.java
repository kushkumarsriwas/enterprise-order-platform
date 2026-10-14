package com.enterprise.auth.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    boolean existsByStoreNameIgnoreCase(String storeName);

    Optional<SellerProfile> findByUserId(Long userId);

    /**
     * Store directory search by store name or city. {@code pattern} is an escaped LIKE pattern
     * (see {@link com.enterprise.auth.common.Like}); JOIN FETCH avoids one extra query per store.
     */
    @Query(value = """
            select s from SellerProfile s join fetch s.user u
            where u.enabled = true and (lower(s.storeName) like :pattern escape '\\'
                                         or lower(s.city) like :pattern escape '\\')""",
            countQuery = """
            select count(s) from SellerProfile s join s.user u
            where u.enabled = true and (lower(s.storeName) like :pattern escape '\\'
                                         or lower(s.city) like :pattern escape '\\')""")
    Page<SellerProfile> searchActive(String pattern, Pageable pageable);
}
