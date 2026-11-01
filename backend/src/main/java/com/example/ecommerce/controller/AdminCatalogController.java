package com.example.ecommerce.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.security.AppUserPrincipal;
import com.example.ecommerce.service.CatalogService;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
public class AdminCatalogController {
    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public java.util.List<ApiDtos.CategoryResponse> categories() {
        return catalogService.adminCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.CategoryResponse createCategory(@Valid @RequestBody ApiDtos.CategoryRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public ApiDtos.CategoryResponse updateCategory(@PathVariable Long id,
                                                   @Valid @RequestBody ApiDtos.CategoryRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCategory(@PathVariable Long id) {
        catalogService.deactivateCategory(id);
    }

    @GetMapping("/products")
    public Page<ApiDtos.ProductResponse> products(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return catalogService.search(q, null, null, null, page, size, sort, true);
    }

    @GetMapping("/products/{id}")
    public ApiDtos.ProductResponse product(@PathVariable Long id) {
        return catalogService.adminProduct(id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ProductResponse createProduct(@Valid @RequestBody ApiDtos.ProductRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return catalogService.createProduct(request, principal.getId());
    }

    @PutMapping("/products/{id}")
    public ApiDtos.ProductResponse updateProduct(@PathVariable Long id,
                                                 @Valid @RequestBody ApiDtos.ProductRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return catalogService.updateProduct(id, request, principal.getId());
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateProduct(@PathVariable Long id) {
        catalogService.deactivateProduct(id);
    }

    @PatchMapping("/inventory")
    public ApiDtos.ProductResponse adjustInventory(
            @Valid @RequestBody ApiDtos.InventoryAdjustmentRequest request,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        return catalogService.adjustInventory(request, principal.getId());
    }
}
