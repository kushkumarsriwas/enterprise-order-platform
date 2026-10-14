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
 * Creates the marketplace's departments on first start. No products are seeded: everything in the
 * catalogue is added by sellers (scripts/demo-data.sh can fill a demo store through the public API).
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
        String[][] departments = {
                {"Electronics", "Phones, laptops, audio and accessories", "smartphone"},
                {"Computers & Office", "Laptops, monitors, printers and office supplies", "laptop"},
                {"Fashion", "Clothing, shoes and accessories", "shirt"},
                {"Home & Kitchen", "Furniture, appliances and kitchenware", "sofa"},
                {"Beauty & Personal Care", "Skincare, fragrances and grooming", "sparkles"},
                {"Grocery", "Food, beverages and household supplies", "shopping-basket"},
                {"Sports & Outdoors", "Fitness equipment and outdoor gear", "dumbbell"},
                {"Books & Stationery", "Books, notebooks and school supplies", "book-open"},
                {"Toys & Baby", "Toys, games and baby products", "baby"},
                {"Automotive", "Car accessories, care and tools", "car"},
                {"Health", "Supplements, first aid and medical supplies", "heart-pulse"},
                {"Tools & Hardware", "Power tools, hand tools and DIY", "wrench"}};
        for (String[] department : departments) {
            categories.save(new Category(department[0], department[1], department[2]));
        }
        log.info("Created {} marketplace departments", departments.length);
    }
}
