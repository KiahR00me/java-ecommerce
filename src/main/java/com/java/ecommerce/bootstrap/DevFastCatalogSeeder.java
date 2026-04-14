package com.java.ecommerce.bootstrap;

import com.java.ecommerce.category.Category;
import com.java.ecommerce.category.CategoryRepository;
import com.java.ecommerce.product.Product;
import com.java.ecommerce.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("dev-fast")
@ConditionalOnProperty(name = "app.seed.dev-fast.enabled", havingValue = "true", matchIfMissing = true)
public class DevFastCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevFastCatalogSeeder.class);

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public DevFastCatalogSeeder(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0 || categoryRepository.count() > 0) {
            log.info("Skipping dev-fast demo seed because catalog data already exists.");
            return;
        }

        Category electronics = createCategory("Electronics", "Laptops, phones, and accessories");
        Category homeOffice = createCategory("Home Office", "Remote work essentials");
        Category fitness = createCategory("Fitness", "Home workout gear");

        createProduct(
                electronics,
                "Mechanical Keyboard Pro",
                "Hot-swappable keyboard for developers",
                "https://images.unsplash.com/photo-1511467687858-23d96c32e4ae?auto=format&fit=crop&w=1200&q=80",
                new BigDecimal("129.00"),
                120);
        createProduct(
                homeOffice,
                "UltraWide 34 Monitor",
                "3440x1440 monitor for productivity",
                "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=1200&q=80",
                new BigDecimal("649.00"),
                35);
        createProduct(
                homeOffice,
                "Adjustable Standing Desk",
                "Electric standing desk with memory presets",
                "https://images.unsplash.com/photo-1598300042247-d088f8ab3a91?auto=format&fit=crop&w=1200&q=80",
                new BigDecimal("499.00"),
                22);
        createProduct(
                fitness,
                "Smart Jump Rope",
                "Bluetooth jump rope with analytics",
                "https://images.unsplash.com/photo-1599058918144-1ffabb6ab9a0?auto=format&fit=crop&w=1200&q=80",
                new BigDecimal("89.00"),
                80);
        createProduct(
                electronics,
                "Noise-Cancelling Headphones",
                "Over-ear ANC headphones for focus",
                "https://images.unsplash.com/photo-1546435770-a3e426bf472b?auto=format&fit=crop&w=1200&q=80",
                new BigDecimal("299.00"),
                45);

        log.info("Seeded dev-fast demo catalog with {} categories and {} products.",
                categoryRepository.count(), productRepository.count());
    }

    private Category createCategory(String name, String description) {
        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return categoryRepository.save(category);
    }

    private void createProduct(Category category, String name, String description, String imageUrl,
            BigDecimal price, int stockQuantity) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setImageUrl(imageUrl);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);
        product.setActive(true);
        product.setCategory(category);
        productRepository.save(product);
    }
}
