package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.entity.Category;
import com.example.ecommerce.entity.InventoryMovement;
import com.example.ecommerce.entity.OrderItem;
import com.example.ecommerce.entity.OrderStatus;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.entity.ProductReview;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.InventoryMovementRepository;
import com.example.ecommerce.repository.OrderItemRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.ProductReviewRepository;
import com.example.ecommerce.repository.UserRepository;

@Service
public class CatalogService {
    private static final Set<String> INVENTORY_REASONS = Set.of("RESTOCK", "CORRECTION", "DAMAGE");
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final ProductReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    public CatalogService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          InventoryMovementRepository inventoryMovementRepository,
                          ProductReviewRepository reviewRepository, OrderItemRepository orderItemRepository,
                          UserRepository userRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.reviewRepository = reviewRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.CategoryResponse> publicCategories() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(this::categoryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiDtos.CategoryResponse> adminCategories() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(this::categoryResponse).toList();
    }

    @Transactional
    public ApiDtos.CategoryResponse createCategory(ApiDtos.CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A category with this name already exists");
        }
        Category category = new Category();
        category.setName(name);
        category.setSlug(uniqueCategorySlug(name, null));
        category.setDescription(normalizeText(request.description()));
        category.setActive(request.active() == null || request.active());
        return categoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public ApiDtos.CategoryResponse updateCategory(Long id, ApiDtos.CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A category with this name already exists");
        }
        category.setName(name);
        category.setSlug(uniqueCategorySlug(name, id));
        category.setDescription(normalizeText(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        return categoryResponse(category);
    }

    @Transactional
    public void deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        category.setActive(false);
    }

    @Transactional(readOnly = true)
    public Page<ApiDtos.ProductResponse> search(String query, Long categoryId, BigDecimal minPrice,
                                                BigDecimal maxPrice, int page, int size, String sort,
                                                boolean admin) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("Page must be non-negative and page size must be between 1 and 100");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw ApiException.badRequest("Minimum price cannot exceed maximum price");
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > 100) {
            throw ApiException.badRequest("Search query must be 100 characters or fewer");
        }

        Specification<Product> specification = (root, criteriaQuery, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!admin) {
                predicates.add(builder.isTrue(root.get("active")));
                Join<Product, Category> category = root.join("category", JoinType.INNER);
                predicates.add(builder.isTrue(category.get("active")));
            }
            if (categoryId != null) {
                predicates.add(builder.equal(root.get("category").get("id"), categoryId));
            }
            if (minPrice != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (!normalizedQuery.isBlank()) {
                String escaped = normalizedQuery.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String pattern = "%" + escaped + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern, '\\'),
                        builder.like(builder.lower(root.get("description")), pattern, '\\')));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<Product> result = productRepository.search(specification,
                PageRequest.of(page, size, sortOrder(sort)));
        return result.map(this::productResponse);
    }

    @Transactional(readOnly = true)
    public ApiDtos.ProductResponse productBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .filter(p -> p.getCategory().isActive())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        return productResponse(product);
    }

    @Transactional(readOnly = true)
    public ApiDtos.ProductResponse adminProduct(Long id) {
        return productRepository.findById(id).map(this::productResponse)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    @Transactional
    public ApiDtos.ProductResponse createProduct(ApiDtos.ProductRequest request, Long adminId) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        String slug = slugify(request.slug());
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("A product with this SKU already exists");
        }
        if (productRepository.existsBySlugIgnoreCase(slug)) {
            throw ApiException.conflict("A product with this slug already exists");
        }

        Product product = new Product();
        applyProduct(product, category, request);
        product.setSku(sku);
        product.setSlug(slug);
        product = productRepository.save(product);
        if (product.getStockQuantity() > 0) {
            recordMovement(product, product.getStockQuantity(), "INITIAL_STOCK", null, null, adminId);
        }
        return productResponse(product);
    }

    @Transactional
    public ApiDtos.ProductResponse updateProduct(Long id, ApiDtos.ProductRequest request, Long adminId) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        String slug = slugify(request.slug());
        if (productRepository.existsBySkuIgnoreCaseAndIdNot(sku, id)) {
            throw ApiException.conflict("A product with this SKU already exists");
        }
        if (productRepository.existsBySlugIgnoreCaseAndIdNot(slug, id)) {
            throw ApiException.conflict("A product with this slug already exists");
        }
        int oldStock = product.getStockQuantity();
        applyProduct(product, category, request);
        product.setSku(sku);
        product.setSlug(slug);
        if (oldStock != product.getStockQuantity()) {
            recordMovement(product, product.getStockQuantity() - oldStock,
                    "CORRECTION", null, null, adminId);
        }
        return productResponse(product);
    }

    @Transactional
    public void deactivateProduct(Long id) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        product.setActive(false);
    }

    @Transactional
    public ApiDtos.ProductResponse adjustInventory(ApiDtos.InventoryAdjustmentRequest request, Long adminId) {
        if (request.quantityDelta() == 0) {
            throw ApiException.badRequest("Inventory adjustment must not be zero");
        }
        String reason = request.reason().trim().toUpperCase(Locale.ROOT);
        if (!INVENTORY_REASONS.contains(reason)) {
            throw ApiException.badRequest("Reason must be RESTOCK, CORRECTION, or DAMAGE");
        }
        Product product = productRepository.findByIdForUpdate(request.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        final int newStock;
        try {
            newStock = Math.addExact(product.getStockQuantity(), request.quantityDelta());
        } catch (ArithmeticException exception) {
            throw ApiException.badRequest("Inventory adjustment exceeds supported limits");
        }
        if (newStock < 0) {
            throw ApiException.conflict("Inventory cannot become negative");
        }
        product.setStockQuantity(newStock);
        recordMovement(product, request.quantityDelta(), reason, null, null, adminId);
        return productResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ApiDtos.ReviewResponse> reviews(Long productId, int page, int size) {
        if (!productRepository.existsByIdAndActiveTrue(productId)) {
            throw ApiException.notFound("Product not found");
        }
        return reviewRepository.findAllByProductIdOrderByCreatedAtDesc(productId, PageRequest.of(page, size))
                .map(review -> new ApiDtos.ReviewResponse(review.getId(), productId,
                        review.getCustomer().getName(), review.getRating(), review.getBody(), review.getCreatedAt()));
    }

    @Transactional
    public ApiDtos.ReviewResponse submitReview(Long customerId, ApiDtos.ReviewRequest request) {
        OrderItem orderItem = orderItemRepository.findByIdAndOrderCustomerId(request.orderItemId(), customerId)
                .orElseThrow(() -> ApiException.notFound("Eligible delivered order item not found"));
        if (orderItem.getOrder().getStatus() != OrderStatus.DELIVERED) {
            throw ApiException.conflict("A review can only be submitted after delivery");
        }
        if (orderItem.getProduct() == null) {
            throw ApiException.conflict("This product is no longer available for review");
        }
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        ProductReview review = new ProductReview();
        review.setCustomer(customer);
        review.setProduct(orderItem.getProduct());
        review.setOrderItem(orderItem);
        review.setRating(request.rating());
        review.setBody(normalizeText(request.body()));
        review = reviewRepository.save(review);
        return new ApiDtos.ReviewResponse(review.getId(), review.getProduct().getId(),
                customer.getName(), review.getRating(), review.getBody(), review.getCreatedAt());
    }

    private void applyProduct(Product product, Category category, ApiDtos.ProductRequest request) {
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(normalizeText(request.description()));
        product.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
        product.setDiscountPercent(request.discountPercent().setScale(2, RoundingMode.HALF_UP));
        product.setStockQuantity(request.stockQuantity());
        product.setImageUrl(request.imageUrl() == null || request.imageUrl().isBlank()
                ? null : request.imageUrl().trim());
        product.setActive(request.active() == null || request.active());
    }

    private void recordMovement(Product product, int delta, String reason, String referenceType,
                               Long referenceId, Long adminId) {
        if (delta == 0) {
            return;
        }
        InventoryMovement movement = new InventoryMovement();
        movement.setProduct(product);
        movement.setQuantityDelta(delta);
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        if (adminId != null) {
            movement.setCreatedBy(userRepository.getReferenceById(adminId));
        }
        inventoryMovementRepository.save(movement);
    }

    private ApiDtos.ProductResponse productResponse(Product product) {
        BigDecimal effective = product.getPrice()
                .multiply(BigDecimal.ONE.subtract(product.getDiscountPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        return new ApiDtos.ProductResponse(product.getId(), product.getCategory().getId(),
                product.getCategory().getName(), product.getSku(), product.getName(), product.getSlug(),
                product.getDescription(), product.getPrice(), product.getDiscountPercent(), effective,
                product.getStockQuantity(), product.getImageUrl(), product.isActive(), product.getCreatedAt());
    }

    private ApiDtos.CategoryResponse categoryResponse(Category category) {
        return new ApiDtos.CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.isActive());
    }

    private String uniqueCategorySlug(String name, Long excludeId) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (categoryRepository.findBySlug(candidate)
                .filter(category -> !category.getId().equals(excludeId)).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String slugify(String value) {
        String normalized = Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            throw ApiException.badRequest("A valid name is required to create a slug");
        }
        return slug;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Sort sortOrder(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "price-asc" -> Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id"));
            case "price-desc" -> Sort.by(Sort.Direction.DESC, "price").and(Sort.by("id"));
            case "name" -> Sort.by(Sort.Direction.ASC, "name").and(Sort.by("id"));
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"));
            default -> throw ApiException.badRequest("Unsupported sort option");
        };
    }
}
