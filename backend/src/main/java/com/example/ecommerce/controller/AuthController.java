package com.example.ecommerce.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.security.AppUserPrincipal;
import com.example.ecommerce.service.AuthService;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private static final String REFRESH_COOKIE = "REFRESH_TOKEN";
    private static final String REFRESH_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final boolean cookieSecure;
    private final String cookieSameSite;
    private final long refreshTokenDays;

    public AuthController(AuthService authService,
                          @Value("${app.cookie.secure:false}") boolean cookieSecure,
                          @Value("${app.cookie.same-site:Lax}") String cookieSameSite,
                          @Value("${app.jwt.refresh-token-days:30}") long refreshTokenDays) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
        this.refreshTokenDays = refreshTokenDays;
    }

    @GetMapping("/auth/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        return new CsrfResponse(csrfToken.getToken());
    }

    @PostMapping("/auth/register")
    public ResponseEntity<ApiDtos.AuthResponse> register(@Valid @RequestBody ApiDtos.RegisterRequest request) {
        return issue(authService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiDtos.AuthResponse> login(@Valid @RequestBody ApiDtos.LoginRequest request) {
        return issue(authService.login(request));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<ApiDtos.AuthResponse> refresh(HttpServletRequest request) {
        return issue(authService.refresh(cookieValue(request)));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookieValue(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }

    @GetMapping("/me")
    public ApiDtos.UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return authService.profile(principal.getId());
    }

    @PatchMapping("/me")
    public ApiDtos.UserResponse updateProfile(@AuthenticationPrincipal AppUserPrincipal principal,
                                              @Valid @RequestBody ApiDtos.ProfileRequest request) {
        return authService.updateProfile(principal.getId(), request);
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                               @Valid @RequestBody ApiDtos.ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }

    private ResponseEntity<ApiDtos.AuthResponse> issue(AuthService.IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
                .body(tokens.response());
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(REFRESH_PATH)
                .maxAge(java.time.Duration.ofDays(refreshTokenDays))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(REFRESH_PATH)
                .maxAge(0)
                .build();
    }

    private String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record CsrfResponse(String token) {
    }
}
