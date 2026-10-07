package com.enterprise.inventory.service;

import com.enterprise.inventory.entity.Product;
import com.enterprise.inventory.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    public ProductService(ProductRepository productRepository, org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
    }

    public Product create(Product product) {
        Product saved = productRepository.save(product);
        redisTemplate.opsForValue().set("product:" + saved.getId(), saved);
        return saved;
    }

    public List<Product> getAll() {
        return productRepository.findAll();
    }

    public Product getById(Long id) {
        Object cached = redisTemplate.opsForValue().get("product:" + id);
        if (cached instanceof Product product) {
            return product;
        }

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
        redisTemplate.opsForValue().set("product:" + id, product);
        return product;
    }

    public Product update(Long id, Product product) {
        Product existing = getById(id);
        existing.setSku(product.getSku());
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        existing.setPrice(product.getPrice());
        existing.setStockQuantity(product.getStockQuantity());
        Product updated = productRepository.save(existing);
        redisTemplate.opsForValue().set("product:" + id, updated);
        return updated;
    }

    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Product not found");
        }
        productRepository.deleteById(id);
        redisTemplate.delete("product:" + id);
    }
}
