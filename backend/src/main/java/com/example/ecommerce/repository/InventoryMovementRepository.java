package com.example.ecommerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.ecommerce.entity.InventoryMovement;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
}
