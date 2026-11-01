package com.example.ecommerce.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.ecommerce.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long accessTokenSeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.access-token-seconds:600}") long accessTokenSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 UTF-8 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenSeconds = accessTokenSeconds;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public String getSubject(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }
}
