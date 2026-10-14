package com.enterprise.inventory.catalog;

import com.enterprise.inventory.catalog.CatalogDtos.CategoryList;
import com.enterprise.inventory.catalog.CatalogDtos.CategoryResponse;
import com.enterprise.inventory.catalog.CatalogDtos.CreateCategoryRequest;
import com.enterprise.inventory.common.ApiException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductCache productCache;

    public CategoryService(CategoryRepository categoryRepository, ProductCache productCache) {
        this.categoryRepository = categoryRepository;
        this.productCache = productCache;
    }

    @Cacheable(cacheNames = ProductCache.CATEGORIES, key = "'all'")
    @Transactional(readOnly = true)
    public CategoryList findAll() {
        return new CategoryList(categoryRepository.findAllWithProductCount());
    }

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("Category '" + name + "' already exists");
        }
        Category category = categoryRepository.save(new Category(name, request.description()));
        productCache.evictCategories();
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(), 0L);
    }
}
