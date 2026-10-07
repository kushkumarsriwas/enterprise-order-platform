package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.Product;
import com.enterprise.inventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, redisTemplate);
    }

    @Test
    void getById_shouldReturnProductFromDatabaseWhenCacheMisses() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:1")).thenReturn(null);

        Product product = new Product("SKU-001", "Test Product", "Test", new BigDecimal("100.00"), 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getById(1L);

        assertEquals("SKU-001", result.getSku());
        verify(productRepository).findById(1L);
        verify(valueOperations).set("product:1", product);
    }

    @Test
    void getById_shouldThrow404WhenProductDoesNotExist() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("product:999")).thenReturn(null);
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> productService.getById(999L));

        assertEquals(404, exception.getStatusCode().value());
        verify(productRepository).findById(999L);
    }

    @Test
    void delete_shouldDeleteDatabaseRecordAndCache() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.delete(1L);

        verify(productRepository).deleteById(1L);
        verify(redisTemplate).delete("product:1");
    }
}
