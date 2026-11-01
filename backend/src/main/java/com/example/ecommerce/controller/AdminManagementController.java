package com.example.ecommerce.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.service.OrderService;

@RestController
@Validated
@RequestMapping("/api/v1/admin")
public class AdminManagementController {
    private final OrderService orderService;

    public AdminManagementController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/dashboard")
    public ApiDtos.DashboardResponse dashboard() {
        return orderService.dashboard();
    }

    @GetMapping("/orders")
    public Page<ApiDtos.OrderResponse> orders(@RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.adminOrders(page, size);
    }

    @PutMapping("/orders/{id}/status")
    public ApiDtos.OrderResponse updateOrderStatus(@PathVariable Long id,
                                                   @Valid @RequestBody ApiDtos.OrderStatusRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    @GetMapping("/customers")
    public Page<ApiDtos.AdminCustomerResponse> customers(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.customers(page, size);
    }

    @PatchMapping("/customers/{id}/enabled")
    @ResponseStatus(HttpStatus.OK)
    public ApiDtos.AdminCustomerResponse setCustomerEnabled(@PathVariable Long id,
            @Valid @RequestBody ApiDtos.CustomerEnabledRequest request) {
        return orderService.setCustomerEnabled(id, request.enabled());
    }
}
