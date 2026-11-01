package com.example.ecommerce.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.domain.Specification;

import com.example.ecommerce.entity.Product;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlugAndActiveTrue(String slug);

    Optional<Product> findByIdAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    Page<Product> findAllByActiveTrue(Pageable pageable);

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    boolean existsByIdAndActiveTrue(Long id);

    default Page<Product> search(Specification<Product> specification, Pageable pageable) {
        return findAll(specification, pageable);
    }
}
