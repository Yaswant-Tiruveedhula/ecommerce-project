package com.example.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @EntityGraph(attributePaths = {"product", "product.category"})
    List<CartItem> findAllByCartIdOrderById(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    Optional<CartItem> findByIdAndCartCustomerId(Long id, Long customerId);

    void deleteAllByCartId(Long cartId);
}
