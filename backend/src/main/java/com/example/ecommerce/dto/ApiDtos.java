package com.example.ecommerce.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must include lowercase, uppercase, and a number")
            String password) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record AuthResponse(String accessToken, long expiresInSeconds, UserResponse user) {
    }

    public record UserResponse(Long id, String name, String email, Set<String> roles, Instant createdAt) {
    }

    public record ProfileRequest(@NotBlank @Size(max = 120) String name) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must include lowercase, uppercase, and a number")
            String newPassword) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 5000) String description,
            Boolean active) {
    }

    public record CategoryResponse(Long id, String name, String slug, String description, boolean active) {
    }

    public record ProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 200) String slug,
            @Size(max = 10000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") BigDecimal price,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent,
            @NotNull @Min(0) @Max(2147483647) Integer stockQuantity,
            @Pattern(regexp = "^(https?://[^\\s]+)?$", message = "Image URL must use HTTP or HTTPS")
            @Size(max = 2048) String imageUrl,
            Boolean active) {
    }

    public record ProductResponse(
            Long id,
            Long categoryId,
            String categoryName,
            String sku,
            String name,
            String slug,
            String description,
            BigDecimal price,
            BigDecimal discountPercent,
            BigDecimal effectivePrice,
            int stockQuantity,
            String imageUrl,
            boolean active,
            Instant createdAt) {
    }

    public record InventoryAdjustmentRequest(
            @NotNull @Positive Long productId,
            @NotNull @Min(-2147483647) @Max(2147483647) Integer quantityDelta,
            @NotBlank @Size(max = 32) String reason) {
    }

    public record CartItemRequest(
            @NotNull @Positive Long productId,
            @NotNull @Min(1) @Max(99) Integer quantity) {
    }

    public record CartQuantityRequest(@NotNull @Min(1) @Max(99) Integer quantity) {
    }

    public record CartResponse(List<CartLineResponse> items, BigDecimal total, int itemCount) {
    }

    public record CartLineResponse(
            Long id,
            Long productId,
            String name,
            String slug,
            String imageUrl,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            int availableStock) {
    }

    public record AddressRequest(
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Size(max = 32) String phone,
            @NotBlank @Size(max = 180) String addressLine1,
            @Size(max = 180) String addressLine2,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 100) String region,
            @NotBlank @Size(max = 24) String postalCode,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
            boolean defaultAddress) {
    }

    public record AddressResponse(
            Long id,
            String recipientName,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            boolean defaultAddress) {
    }

    public record CheckoutRequest(
            @NotNull @Positive Long addressId,
            @NotBlank @Size(max = 100) String idempotencyKey,
            @NotBlank String paymentMethod) {
    }

    public record OrderStatusRequest(@NotNull OrderState status) {
    }

    public enum OrderState {
        PENDING,
        CONFIRMED,
        PROCESSING,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        REFUNDED
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            String status,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal shippingTotal,
            BigDecimal total,
            String paymentMethod,
            String paymentStatus,
            String recipientName,
            String phone,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            List<OrderLineResponse> items,
            Instant createdAt) {
    }

    public record OrderLineResponse(
            Long id,
            Long productId,
            String productName,
            String sku,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public record ReviewRequest(@NotNull @Positive Long orderItemId,
                                @NotNull @Min(1) @Max(5) Integer rating,
                                @Size(max = 3000) String body) {
    }

    public record ReviewResponse(
            Long id,
            Long productId,
            String customerName,
            int rating,
            String body,
            Instant createdAt) {
    }

    public record DashboardResponse(
            long customers,
            long products,
            long orders,
            BigDecimal revenue,
            long pendingOrders) {
    }

    public record AdminCustomerResponse(Long id, String name, String email, boolean enabled, Instant createdAt) {
    }

    public record CustomerEnabledRequest(boolean enabled) {
    }
}
