package com.example.ecommerce.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.security.AppUserPrincipal;
import com.example.ecommerce.service.CatalogService;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final CatalogService catalogService;

    public ReviewController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ReviewResponse submit(@AuthenticationPrincipal AppUserPrincipal principal,
                                         @Valid @RequestBody ApiDtos.ReviewRequest request) {
        return catalogService.submitReview(principal.getId(), request);
    }
}
