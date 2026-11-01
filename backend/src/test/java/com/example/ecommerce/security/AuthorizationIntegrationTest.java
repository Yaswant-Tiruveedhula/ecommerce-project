package com.example.ecommerce.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.entity.Category;
import com.example.ecommerce.entity.Product;
import com.example.ecommerce.repository.CategoryRepository;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.repository.PurchaseOrderRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(scripts = "/test-roles.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AuthorizationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PurchaseOrderRepository orderRepository;

    private Cookie csrfCookie;
    private String csrfToken;

    @BeforeEach
    void obtainCsrfToken() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        csrfToken = body.get("token").asText();
        csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void registrationCreatesCustomerOnlyAndStoresPasswordHash() throws Exception {
        String email = "customer-" + System.nanoTime() + "@example.test";
        String body = """
                {"name":"Casey Customer","email":"%s","password":"SecurePass123"}
                """.formatted(email);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals("SecurePass123", user.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(user.getPasswordHash().startsWith("$2"));
        org.junit.jupiter.api.Assertions.assertEquals("REFRESH_TOKEN",
                result.getResponse().getHeader("Set-Cookie").split("=", 2)[0]);
    }

    @Test
    void customerCannotAccessAdminApiAndUnauthenticatedRequestIsUnauthorized() throws Exception {
        String email = "customer-" + System.nanoTime() + "@example.test";
        String body = """
                {"name":"Casey Customer","email":"%s","password":"SecurePass123"}
                """.formatted(email);
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(registration.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/dashboard").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void registrationRejectsWeakPasswords() throws Exception {
        String email = "weak-" + System.nanoTime() + "@example.test";
        String body = """
                {"name":"Casey Customer","email":"%s","password":"weak"}
                """.formatted(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void checkoutUsesServerPricesIsIdempotentAndOrdersAreOwnerScoped() throws Exception {
        String customerEmail = "buyer-" + System.nanoTime() + "@example.test";
        JsonNode customer = register(customerEmail);
        String customerToken = customer.get("accessToken").asText();
        Product product = createProduct();

        MvcResult addressResult = mockMvc.perform(post("/api/v1/addresses")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientName":"Casey Customer","phone":"555-0100",
                                 "addressLine1":"1 Market Street","city":"Springfield",
                                 "region":"CA","postalCode":"90001","countryCode":"US",
                                 "defaultAddress":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long addressId = objectMapper.readTree(addressResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(post("/api/v1/cart/items")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":%d,"quantity":2}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(20.0));

        String idempotencyKey = "checkout-" + System.nanoTime();
        String checkoutBody = """
                {"addressId":%d,"idempotencyKey":"%s","paymentMethod":"CASH_ON_DELIVERY"}
                """.formatted(addressId, idempotencyKey);
        MvcResult checkout = mockMvc.perform(post("/api/v1/orders/checkout")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(20.0))
                .andExpect(jsonPath("$.paymentMethod").value("CASH_ON_DELIVERY"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andReturn();
        JsonNode order = objectMapper.readTree(checkout.getResponse().getContentAsString());
        long orderId = order.get("id").asLong();
        String orderNumber = order.get("orderNumber").asText();

        mockMvc.perform(post("/api/v1/orders/checkout")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber));
        org.junit.jupiter.api.Assertions.assertEquals(3,
                productRepository.findById(product.getId()).orElseThrow().getStockQuantity());
        org.junit.jupiter.api.Assertions.assertEquals(1, orderRepository.count());

        String otherEmail = "other-" + System.nanoTime() + "@example.test";
        String otherToken = register(otherEmail).get("accessToken").asText();
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private JsonNode register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casey Customer","email":"%s","password":"SecurePass123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Product createProduct() {
        Category category = new Category();
        category.setName("Test category " + System.nanoTime());
        category.setSlug("test-category-" + System.nanoTime());
        category.setActive(true);
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setCategory(category);
        product.setSku("TEST-" + System.nanoTime());
        product.setName("Test product");
        product.setSlug("test-product-" + System.nanoTime());
        product.setPrice(new java.math.BigDecimal("10.00"));
        product.setDiscountPercent(java.math.BigDecimal.ZERO);
        product.setStockQuantity(5);
        product.setActive(true);
        return productRepository.save(product);
    }
}
