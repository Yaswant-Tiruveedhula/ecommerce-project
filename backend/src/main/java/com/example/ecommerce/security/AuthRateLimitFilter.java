package com.example.ecommerce.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Cache<String, AttemptWindow> attempts = Caffeine.newBuilder()
            .maximumSize(20_000)
            .expireAfterAccess(WINDOW)
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientAddress = request.getRemoteAddr();
        AttemptWindow window = attempts.asMap().compute(clientAddress, (key, current) -> {
            Instant now = Instant.now();
            if (current == null || current.startedAt().plus(WINDOW).isBefore(now)) {
                return new AttemptWindow(now, new AtomicInteger(1));
            }
            current.attempts().incrementAndGet();
            return current;
        });

        if (window.attempts().get() > MAX_ATTEMPTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(WINDOW.toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"status":429,"error":"Too Many Requests","message":"Too many authentication attempts. Try again later."}
                    """);
            return;
        }
        chain.doFilter(request, response);
    }

    private record AttemptWindow(Instant startedAt, AtomicInteger attempts) {
    }
}
