package com.example.ecommerce.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.security.AppUserPrincipal;
import com.example.ecommerce.service.OrderService;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.OrderResponse checkout(@AuthenticationPrincipal AppUserPrincipal principal,
                                          @Valid @RequestBody ApiDtos.CheckoutRequest request) {
        return orderService.checkout(principal.getId(), request);
    }

    @GetMapping
    public Page<ApiDtos.OrderResponse> list(@AuthenticationPrincipal AppUserPrincipal principal,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return orderService.customerOrders(principal.getId(), page, size);
    }

    @GetMapping("/{id}")
    public ApiDtos.OrderResponse detail(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @PathVariable Long id) {
        return orderService.customerOrder(principal.getId(), id);
    }

    @PostMapping("/{id}/cancel")
    public ApiDtos.OrderResponse cancel(@AuthenticationPrincipal AppUserPrincipal principal,
                                        @PathVariable Long id) {
        return orderService.cancelByCustomer(principal.getId(), id);
    }
}
