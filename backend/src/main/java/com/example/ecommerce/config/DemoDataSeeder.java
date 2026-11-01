package com.example.ecommerce.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entity.Category;
import com.example.ecommerce.entity.InventoryMovement;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.InventoryMovementRepository;
import com.example.ecommerce.repository.ProductRepository;

@Component
@ConditionalOnProperty(name = "app.seed.demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final InventoryMovementRepository movementRepository;

    public DemoDataSeeder(CategoryRepository categoryRepository, ProductRepository productRepository,
                          InventoryMovementRepository movementRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.movementRepository = movementRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        Category home = category("Home & living");
        Category kitchen = category("Kitchen");
        Category personal = category("Personal care");
        Category stationery = category("Paper goods");
        List<SampleProduct> products = List.of(
                new SampleProduct(home, "CG-HOME-001", "Everyday stoneware vase", "stoneware-vase",
                        "A softly shaped stoneware vase for a few stems or a quiet corner.",
                        "/images/products/stoneware-vase.jpg", "34.00", "0", 18),
                new SampleProduct(home, "CG-HOME-002", "Woven cotton throw", "woven-cotton-throw",
                        "A breathable cotton throw with a relaxed, textured weave.",
                        "/images/products/woven-cotton-throw.jpg", "68.00", "10", 12),
                new SampleProduct(home, "CG-HOME-003", "Cedar candle", "cedar-candle",
                        "A warm cedar and citrus candle poured in a reusable glass vessel.",
                        "/images/products/cedar-candle.jpg", "28.00", "0", 25),
                new SampleProduct(kitchen, "CG-KITCHEN-001", "Oak serving board", "oak-serving-board",
                        "A durable serving board made for slow breakfasts and shared tables.",
                        "/images/products/oak-serving-board.jpg", "46.00", "0", 14),
                new SampleProduct(kitchen, "CG-KITCHEN-002", "Linen tea towels, set of two", "linen-tea-towels",
                        "Absorbent linen-blend towels that soften with every wash.",
                        "/images/products/linen-tea-towels.jpg", "24.00", "5", 30),
                new SampleProduct(personal, "CG-CARE-001", "Daily hand balm", "daily-hand-balm",
                        "A nourishing, lightly scented balm in a pocket-sized tin.",
                        "/images/products/daily-hand-balm.jpg", "18.00", "0", 40),
                new SampleProduct(stationery, "CG-PAPER-001", "Undated daily notebook", "daily-notebook",
                        "A lay-flat, undated notebook for lists, notes, and plans.",
                        "/images/products/daily-notebook.jpg", "22.00", "0", 35),
                new SampleProduct(stationery, "CG-PAPER-002", "Recycled note cards", "recycled-note-cards",
                        "A set of twelve blank note cards on recycled uncoated stock.",
                        "/images/products/recycled-note-cards.jpg", "16.00", "0", 50));
        for (SampleProduct sample : products) {
            Product product = new Product();
            product.setCategory(sample.category());
            product.setSku(sample.sku());
            product.setName(sample.name());
            product.setSlug(sample.slug());
            product.setDescription(sample.description());
            product.setImageUrl(sample.imageUrl());
            product.setPrice(new BigDecimal(sample.price()));
            product.setDiscountPercent(new BigDecimal(sample.discount()));
            product.setStockQuantity(sample.stock());
            product.setActive(true);
            product = productRepository.save(product);

            InventoryMovement movement = new InventoryMovement();
            movement.setProduct(product);
            movement.setQuantityDelta(sample.stock());
            movement.setReason("INITIAL_STOCK");
            movementRepository.save(movement);
        }
    }

    private Category category(String name) {
        return categoryRepository.findBySlug(slug(name)).orElseGet(() -> {
            Category category = new Category();
            category.setName(name);
            category.setSlug(slug(name));
            category.setActive(true);
            return categoryRepository.save(category);
        });
    }

    private String slug(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private record SampleProduct(Category category, String sku, String name, String slug,
                                 String description, String imageUrl, String price, String discount, int stock) {
    }
}
