package com.enterprise.inventory.repository;

import com.enterprise.inventory.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
class ProductRepositoryIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private ProductRepository productRepository;

    @Test
    void saveAndFindProduct_shouldWorkWithRealPostgreSQL() {
        Product product = new Product("TC-001", "Testcontainers Product", "PostgreSQL integration test", new BigDecimal("99.99"), 25);

        Product saved = productRepository.save(product);

        assertNotNull(saved.getId());
        assertEquals("TC-001", saved.getSku());
        assertEquals(25, saved.getStockQuantity());

        Product found = productRepository.findBySku("TC-001").orElseThrow();
        assertEquals("Testcontainers Product", found.getName());
    }
}
