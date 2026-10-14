package com.enterprise.inventory.config;

import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.catalog.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the marketplace's category taxonomy on first start. No products are seeded:
 * everything on sale is added by real merchants.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categories;

    public DataSeeder(CategoryRepository categories) {
        this.categories = categories;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categories.count() > 0) {
            return;
        }
        String[][] taxonomy = {
                {"Electronics", "Phones, laptops, audio and accessories"},
                {"Fashion", "Clothing, shoes and accessories"},
                {"Home & Kitchen", "Furniture, appliances and kitchenware"},
                {"Beauty & Personal Care", "Skincare, fragrances and grooming"},
                {"Grocery", "Food, beverages and household supplies"},
                {"Sports & Outdoors", "Fitness equipment and outdoor gear"},
                {"Books & Stationery", "Books, office and school supplies"},
                {"Toys & Baby", "Toys, games and baby products"},
                {"Automotive", "Car accessories and tools"},
                {"Health", "Supplements and medical supplies"}};
        for (String[] category : taxonomy) {
            categories.save(new Category(category[0], category[1]));
        }
        log.info("Created {} marketplace categories", taxonomy.length);
    }
}
