package com.enterprise.inventory.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    /** Category list with the number of active products in each (LEFT JOIN + GROUP BY). */
    @Query("""
            select new com.enterprise.inventory.catalog.CatalogDtos$CategoryResponse(c.id, c.name, c.description, count(p.id))
            from Category c left join Product p on p.category = c and p.active = true
            group by c.id, c.name, c.description
            order by c.name""")
    List<CatalogDtos.CategoryResponse> findAllWithProductCount();
}
