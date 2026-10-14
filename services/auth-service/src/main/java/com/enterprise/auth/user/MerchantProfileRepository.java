package com.enterprise.auth.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, Long> {

    boolean existsByStoreNameIgnoreCase(String storeName);

    Optional<MerchantProfile> findByUserId(Long userId);

    @Query("""
            select m from MerchantProfile m join fetch m.user u
            where u.enabled = true and (lower(m.storeName) like lower(concat('%', :search, '%'))
                                         or lower(m.city) like lower(concat('%', :search, '%')))""")
    Page<MerchantProfile> searchActive(String search, Pageable pageable);
}
