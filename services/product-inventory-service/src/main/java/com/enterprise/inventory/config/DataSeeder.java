package com.enterprise.inventory.config;

import com.enterprise.inventory.catalog.Category;
import com.enterprise.inventory.catalog.CategoryRepository;
import com.enterprise.inventory.catalog.Product;
import com.enterprise.inventory.catalog.ProductRepository;
import com.enterprise.inventory.stock.Inventory;
import com.enterprise.inventory.stock.InventoryRepository;
import com.enterprise.inventory.stock.MovementType;
import com.enterprise.inventory.stock.StockMovement;
import com.enterprise.inventory.stock.StockMovementRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads a small demo catalogue on the very first start so the shop is never empty. */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final StockMovementRepository movements;

    public DataSeeder(CategoryRepository categories, ProductRepository products, InventoryRepository inventory,
                      StockMovementRepository movements) {
        this.categories = categories;
        this.products = products;
        this.inventory = inventory;
        this.movements = movements;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categories.count() > 0 || products.count() > 0) {
            return;
        }
        Category laptops = categories.save(new Category("Laptops", "Business and developer laptops"));
        Category phones = categories.save(new Category("Smartphones", "Phones and tablets"));
        Category accessories = categories.save(new Category("Accessories", "Keyboards, mice, audio and more"));
        Category office = categories.save(new Category("Office", "Furniture and office equipment"));

        add("LAP-DXPS-15", "Dell XPS 15", "15.6\" OLED, Intel Core Ultra 9, 32 GB RAM, 1 TB SSD", "7499.00", laptops, 12, 3);
        add("LAP-MBP-14", "MacBook Pro 14 M4", "Apple M4 Pro, 24 GB unified memory, 512 GB SSD", "8299.00", laptops, 8, 3);
        add("LAP-TP-X1", "Lenovo ThinkPad X1 Carbon", "14\" 2.8K, Intel Core Ultra 7, 32 GB RAM", "6199.00", laptops, 4, 5);
        add("PHN-IP16-PRO", "iPhone 16 Pro", "256 GB, Desert Titanium", "4699.00", phones, 25, 5);
        add("PHN-S25-ULT", "Samsung Galaxy S25 Ultra", "512 GB, Titanium Black", "5199.00", phones, 15, 5);
        add("ACC-MX-KEYS", "Logitech MX Keys S", "Wireless illuminated keyboard", "449.00", accessories, 40, 10);
        add("ACC-MX-MASTER", "Logitech MX Master 3S", "Ergonomic wireless mouse", "399.00", accessories, 3, 10);
        add("ACC-SONY-XM5", "Sony WH-1000XM5", "Noise-cancelling headphones", "1299.00", accessories, 18, 5);
        add("OFF-ERGO-CHAIR", "Ergonomic Office Chair", "Mesh back, lumbar support, adjustable arms", "1150.00", office, 6, 2);
        add("OFF-DESK-STAND", "Electric Standing Desk", "160x80 cm, dual motor, memory presets", "1890.00", office, 0, 2);
        log.info("Seeded demo catalogue: 4 categories, 10 products");
    }

    private void add(String sku, String name, String description, String price, Category category, int stock,
                     int reorderLevel) {
        Product product = products.save(new Product(sku, name, description, new BigDecimal(price), category, null));
        inventory.save(new Inventory(product, stock, reorderLevel));
        movements.save(new StockMovement(product.getId(), MovementType.INITIAL, stock, null, "Demo data", "system"));
    }
}
