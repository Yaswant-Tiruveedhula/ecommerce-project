package com.example.ecommerce.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.security.AppUserPrincipal;
import com.example.ecommerce.service.CartService;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiDtos.CartResponse get(@AuthenticationPrincipal AppUserPrincipal principal) {
        return cartService.get(principal.getId());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public ApiDtos.CartResponse add(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @Valid @RequestBody ApiDtos.CartItemRequest request) {
        return cartService.add(principal.getId(), request);
    }

    @PutMapping("/items/{id}")
    public ApiDtos.CartResponse update(@AuthenticationPrincipal AppUserPrincipal principal,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ApiDtos.CartQuantityRequest request) {
        return cartService.updateQuantity(principal.getId(), id, request.quantity());
    }

    @DeleteMapping("/items/{id}")
    public ApiDtos.CartResponse remove(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        return cartService.remove(principal.getId(), id);
    }

    @DeleteMapping
    public ApiDtos.CartResponse clear(@AuthenticationPrincipal AppUserPrincipal principal) {
        return cartService.clear(principal.getId());
    }
}
