package com.example.ecommerce.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entity.User;
import com.example.ecommerce.repository.RoleRepository;
import com.example.ecommerce.repository.UserRepository;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final String email;
    private final String password;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(@Value("${ADMIN_BOOTSTRAP_EMAIL:}") String email,
                          @Value("${ADMIN_BOOTSTRAP_PASSWORD:}") String password,
                          UserRepository userRepository, RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder) {
        this.email = email;
        this.password = password;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.isBlank() || password.length() < 16 || password.length() > 72) {
            throw new IllegalStateException(
                    "Set both ADMIN_BOOTSTRAP_EMAIL and a 16-72 character ADMIN_BOOTSTRAP_PASSWORD");
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        var existing = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            boolean alreadyAdmin = existing.get().getRoles().stream().anyMatch(role -> role.getName().equals("ADMIN"));
            if (alreadyAdmin) {
                return;
            }
            throw new IllegalStateException(
                    "Admin bootstrap email already belongs to a non-admin account; choose an unused email");
        }

        User admin = new User();
        admin.setName("Store Administrator");
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.getRoles().add(roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing from the database")));
        userRepository.save(admin);
    }
}
