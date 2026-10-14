package com.enterprise.auth.keys;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

    List<SigningKey> findAllByOrderByCreatedAtDesc();
}
