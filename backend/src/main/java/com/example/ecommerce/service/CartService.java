package com.example.ecommerce.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.entity.CartItem;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.UserRepository;

@Service
public class CartService {
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductRepository productRepository, UserRepository userRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ApiDtos.CartResponse get(Long customerId) {
        return cartRepository.findByCustomerId(customerId)
                .map(this::response)
                .orElseGet(() -> new ApiDtos.CartResponse(List.of(), BigDecimal.ZERO.setScale(2), 0));
    }

    @Transactional
    public ApiDtos.CartResponse add(Long customerId, ApiDtos.CartItemRequest request) {
        Cart cart = lockedCart(customerId);
        Product product = productRepository.findById(request.productId())
                .filter(p -> p.isActive() && p.getCategory().isActive())
                .orElseThrow(() -> ApiException.notFound("Product is not available"));
        if (product.getStockQuantity() < 1) {
            throw ApiException.conflict("Product is out of stock");
        }
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId())
                .orElseGet(() -> {
                    CartItem newItem = new CartItem();
                    newItem.setCart(cart);
                    newItem.setProduct(product);
                    newItem.setQuantity(0);
                    return newItem;
                });
        int quantity = item.getQuantity() + request.quantity();
        if (quantity > 99 || quantity > product.getStockQuantity()) {
            throw ApiException.conflict("Requested quantity exceeds available stock");
        }
        item.setQuantity(quantity);
        cart.setUpdatedAt(Instant.now());
        cartItemRepository.save(item);
        return response(cart);
    }

    @Transactional
    public ApiDtos.CartResponse updateQuantity(Long customerId, Long itemId, int quantity) {
        Cart cart = lockedCart(customerId);
        CartItem item = cartItemRepository.findByIdAndCartCustomerId(itemId, customerId)
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));
        if (!item.getProduct().isActive() || !item.getProduct().getCategory().isActive()
                || item.getProduct().getStockQuantity() < quantity) {
            throw ApiException.conflict("Requested quantity exceeds available stock");
        }
        item.setQuantity(quantity);
        cart.setUpdatedAt(Instant.now());
        return response(cart);
    }

    @Transactional
    public ApiDtos.CartResponse remove(Long customerId, Long itemId) {
        Cart cart = lockedCart(customerId);
        CartItem item = cartItemRepository.findByIdAndCartCustomerId(itemId, customerId)
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));
        cartItemRepository.delete(item);
        cart.setUpdatedAt(Instant.now());
        return response(cart);
    }

    @Transactional
    public ApiDtos.CartResponse clear(Long customerId) {
        Cart cart = lockedCart(customerId);
        cartItemRepository.deleteAllByCartId(cart.getId());
        cart.setUpdatedAt(Instant.now());
        return new ApiDtos.CartResponse(List.of(), BigDecimal.ZERO.setScale(2), 0);
    }

    private Cart lockedCart(Long customerId) {
        return cartRepository.findByCustomerIdForUpdate(customerId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setCustomer(userRepository.getReferenceById(customerId));
            return cartRepository.saveAndFlush(cart);
        });
    }

    private ApiDtos.CartResponse response(Cart cart) {
        List<ApiDtos.CartLineResponse> lines = cartItemRepository.findAllByCartIdOrderById(cart.getId()).stream()
                .map(item -> {
                    Product product = item.getProduct();
                    BigDecimal unitPrice = effectivePrice(product);
                    return new ApiDtos.CartLineResponse(item.getId(), product.getId(), product.getName(),
                            product.getSlug(), product.getImageUrl(), unitPrice, item.getQuantity(),
                            unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2, RoundingMode.HALF_UP),
                            product.getStockQuantity());
                }).toList();
        BigDecimal total = lines.stream().map(ApiDtos.CartLineResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        int count = lines.stream().mapToInt(ApiDtos.CartLineResponse::quantity).sum();
        return new ApiDtos.CartResponse(lines, total, count);
    }

    private BigDecimal effectivePrice(Product product) {
        return product.getPrice()
                .multiply(BigDecimal.ONE.subtract(product.getDiscountPercent().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
