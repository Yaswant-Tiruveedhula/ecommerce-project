package com.example.ecommerce.controller;

import java.math.BigDecimal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.service.CatalogService;

@RestController
@Validated
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public java.util.List<ApiDtos.CategoryResponse> categories() {
        return catalogService.publicCategories();
    }

    @GetMapping("/products")
    public Page<ApiDtos.ProductResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return catalogService.search(q, categoryId, minPrice, maxPrice, page, size, sort, false);
    }

    @GetMapping("/products/{slug}")
    public ApiDtos.ProductResponse product(@PathVariable String slug) {
        return catalogService.productBySlug(slug);
    }

    @GetMapping("/products/{slug}/reviews")
    public Page<ApiDtos.ReviewResponse> reviews(@PathVariable String slug,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        ApiDtos.ProductResponse product = catalogService.productBySlug(slug);
        return catalogService.reviews(product.id(), page, size);
    }
}
