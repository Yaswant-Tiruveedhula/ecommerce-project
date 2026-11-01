package com.example.ecommerce.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dto.ApiDtos;
import com.example.ecommerce.entity.RefreshToken;
import com.example.ecommerce.entity.Role;
import com.example.ecommerce.entity.User;
import com.example.ecommerce.entity.Cart;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repository.CartRepository;
import com.example.ecommerce.repository.RefreshTokenRepository;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtService;

@Service
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenDays;

    public AuthService(UserRepository userRepository, CartRepository cartRepository, RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${app.jwt.refresh-token-days:30}") long refreshTokenDays) {
        this.userRepository = userRepository;
        this.cartRepository = cartRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenDays = refreshTokenDays;
    }

    @Transactional
    public IssuedTokens register(ApiDtos.RegisterRequest request) {
        validateBcryptPasswordLength(request.password());
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }

        Role customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is missing from the database"));
        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(customerRole);
        user = userRepository.save(user);
        Cart cart = new Cart();
        cart.setCustomer(user);
        cartRepository.save(cart);
        return issueTokens(user);
    }

    @Transactional
    public IssuedTokens login(ApiDtos.LoginRequest request) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException exception) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return issueTokens(user);
    }

    @Transactional
    public IssuedTokens refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.unauthorized("Refresh token is missing");
        }

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token is invalid or expired"));
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(Instant.now())
                || !current.getUser().isEnabled()) {
            throw ApiException.unauthorized("Refresh token is invalid or expired");
        }

        current.setRevokedAt(Instant.now());
        return issueTokens(current.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public ApiDtos.UserResponse profile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return toUserResponse(user);
    }

    @Transactional
    public ApiDtos.UserResponse updateProfile(Long userId, ApiDtos.ProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setName(request.name().trim());
        return toUserResponse(user);
    }

    @Transactional
    public void changePassword(Long userId, ApiDtos.ChangePasswordRequest request) {
        validateBcryptPasswordLength(request.newPassword());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllByUserId(userId, Instant.now());
    }

    private IssuedTokens issueTokens(User user) {
        String rawRefreshToken = newRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawRefreshToken));
        refreshToken.setExpiresAt(Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
        return new IssuedTokens(
                new ApiDtos.AuthResponse(jwtService.createAccessToken(user),
                        jwtService.getAccessTokenSeconds(), toUserResponse(user)),
                rawRefreshToken);
    }

    private ApiDtos.UserResponse toUserResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ApiDtos.UserResponse(user.getId(), user.getName(), user.getEmail(), roles, user.getCreatedAt());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateBcryptPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must not exceed 72 UTF-8 bytes");
        }
    }

    public record IssuedTokens(ApiDtos.AuthResponse response, String refreshToken) {
    }
}
