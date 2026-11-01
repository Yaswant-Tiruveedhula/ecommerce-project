package com.example.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entity.PurchaseOrder;
import com.example.ecommerce.entity.OrderStatus;
import jakarta.persistence.LockModeType;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findByIdAndCustomerId(Long id, Long customerId);

    Page<PurchaseOrder> findAllByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<PurchaseOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<PurchaseOrder> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.total), 0) from PurchaseOrder o where o.status = :status")
    java.math.BigDecimal sumTotalByStatus(@Param("status") OrderStatus status);
}
