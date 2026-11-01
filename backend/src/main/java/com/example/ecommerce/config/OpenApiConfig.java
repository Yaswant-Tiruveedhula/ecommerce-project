package com.example.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI ecommerceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Common Goods Store API")
                .description("REST API for catalog, accounts, carts, checkout, and store administration.")
                .version("v1"));
    }
}
