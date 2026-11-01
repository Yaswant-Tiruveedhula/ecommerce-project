package com.example.ecommerce.controller;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.security.AppUserPrincipal;
import com.example.ecommerce.service.AddressService;

@RestController
@RequestMapping("/api/v1/addresses")
public class AccountController {
    private final AddressService addressService;

    public AccountController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<ApiDtos.AddressResponse> list(@AuthenticationPrincipal AppUserPrincipal principal) {
        return addressService.list(principal.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.AddressResponse create(@AuthenticationPrincipal AppUserPrincipal principal,
                                          @Valid @RequestBody ApiDtos.AddressRequest request) {
        return addressService.create(principal.getId(), request);
    }

    @PutMapping("/{id}")
    public ApiDtos.AddressResponse update(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @PathVariable Long id,
                                         @Valid @RequestBody ApiDtos.AddressRequest request) {
        return addressService.update(principal.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserPrincipal principal, @PathVariable Long id) {
        addressService.delete(principal.getId(), id);
    }
}
