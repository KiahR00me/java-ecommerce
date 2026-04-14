package com.java.ecommerce;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import com.java.ecommerce.customer.Customer;
import com.java.ecommerce.customer.CustomerRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-fast")
class Stage1FlowIntegrationTests {

        private static final HttpClient HTTP = HttpClient.newHttpClient();
        private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

        @Autowired
        private CustomerRepository customerRepository;

        @LocalServerPort
        private int port;

        @Test
        void stage1Flow_createCatalogCustomerCartAndCheckout() throws Exception {
                String suffix = UUID.randomUUID().toString().substring(0, 8);

                String categoryJson = """
                                {
                                  "name": "Electronics-%s",
                                  "description": "Devices and accessories"
                                }
                                """.formatted(suffix);

                HttpResponse<String> categoryResponse = send("/api/categories", "POST", categoryJson, "admin",
                                "admin123");
                assertEquals(201, categoryResponse.statusCode());

                Long categoryId = ((Number) JsonPath.read(categoryResponse.body(), "$.id")).longValue();

                String productJson = """
                                {
                                  "name": "Laptop-%s",
                                  "description": "14-inch development laptop",
                                  "price": 1299.99,
                                  "stockQuantity": 10,
                                  "categoryId": %d,
                                  "active": true
                                }
                                """.formatted(suffix, categoryId);

                HttpResponse<String> productResponse = send("/api/products", "POST", productJson, "admin", "admin123");
                assertEquals(201, productResponse.statusCode());

                Long productId = ((Number) JsonPath.read(productResponse.body(), "$.id")).longValue();
                BigDecimal price = new BigDecimal(JsonPath.read(productResponse.body(), "$.price").toString());

                Long customerId = ensureCustomerExists("customer@example.com", "Portfolio Shopper");

                String addItemJson = """
                                {
                                  "productId": %d,
                                  "quantity": 2
                                }
                                """.formatted(productId);

                HttpResponse<String> addItemResponse = send(
                                "/api/carts/%d/items".formatted(customerId),
                                "POST",
                                addItemJson,
                                "customer@example.com",
                                "customer123");
                assertEquals(200, addItemResponse.statusCode());
                assertEquals(productId.longValue(),
                                ((Number) JsonPath.read(addItemResponse.body(), "$.items[0].product.id")).longValue());
                assertEquals(2, ((Number) JsonPath.read(addItemResponse.body(), "$.items[0].quantity")).intValue());

                String checkoutJson = """
                                {
                                  "customerId": %d
                                }
                                """.formatted(customerId);

                BigDecimal expectedTotal = price.multiply(BigDecimal.valueOf(2));

                HttpResponse<String> checkoutResponse = send(
                                "/api/orders/checkout",
                                "POST",
                                checkoutJson,
                                "customer@example.com",
                                "customer123");
                assertEquals(201, checkoutResponse.statusCode());

                assertEquals(customerId.longValue(),
                                ((Number) JsonPath.read(checkoutResponse.body(), "$.customer.id")).longValue());
                assertEquals("PENDING", JsonPath.read(checkoutResponse.body(), "$.status"));
                assertEquals(productId.longValue(),
                                ((Number) JsonPath.read(checkoutResponse.body(), "$.items[0].product.id")).longValue());
                assertEquals(2, ((Number) JsonPath.read(checkoutResponse.body(), "$.items[0].quantity")).intValue());
                BigDecimal totalAmount = new BigDecimal(
                                JsonPath.read(checkoutResponse.body(), "$.totalAmount").toString());
                assertTrue(totalAmount.compareTo(expectedTotal) == 0);
        }

        @Test
        void adminEndpoints_areProtected() throws Exception {
                String payload = """
                                {
                                  "name": "Blocked",
                                  "description": "Should not pass"
                                }
                                """;

                HttpResponse<String> unauthorized = send("/api/categories", "POST", payload, null, null);
                assertEquals(401, unauthorized.statusCode());

                HttpResponse<String> forbidden = send("/api/categories", "POST", payload, "customer@example.com",
                                "customer123");
                assertEquals(403, forbidden.statusCode());

                HttpResponse<String> customerList = send("/api/customers", "GET", null, null, null);
                assertEquals(401, customerList.statusCode());

                String createCustomerPayload = """
                                {
                                  "email": "public-signup@example.com",
                                  "fullName": "Public Signup"
                                }
                                """;
                HttpResponse<String> publicCreateAttempt = send("/api/customers", "POST", createCustomerPayload, null,
                                null);
                assertEquals(401, publicCreateAttempt.statusCode());

                Long customerId = ensureCustomerExists("customer@example.com", "Portfolio Shopper");

                HttpResponse<String> customerListAsCustomer = send("/api/customers", "GET", null,
                                "customer@example.com",
                                "customer123");
                assertEquals(403, customerListAsCustomer.statusCode());

                HttpResponse<String> meAsCustomer = send("/api/customers/me", "GET", null, "customer@example.com",
                                "customer123");
                assertEquals(200, meAsCustomer.statusCode());
                assertEquals(customerId.longValue(), ((Number) JsonPath.read(meAsCustomer.body(), "$.id")).longValue());
                assertEquals("customer@example.com", JsonPath.read(meAsCustomer.body(), "$.email"));
        }

        @Test
        void adminProvisioningAndVerificationFlow_works() throws Exception {
                String suffix = UUID.randomUUID().toString().substring(0, 8);
                String email = "verify-" + suffix + "@example.com";

                String createPayload = """
                                {
                                  "email": "%s",
                                  "fullName": "Verification Candidate"
                                }
                                """.formatted(email);

                HttpResponse<String> createResponse = send("/api/customers", "POST", createPayload, "admin",
                                "admin123");
                assertEquals(201, createResponse.statusCode());
                Long customerId = ((Number) JsonPath.read(createResponse.body(), "$.id")).longValue();
                assertEquals(false, JsonPath.read(createResponse.body(), "$.emailVerified"));

                HttpResponse<String> sendMailResponse = send(
                                "/api/customers/%d/send-verification".formatted(customerId),
                                "POST",
                                null,
                                "admin",
                                "admin123");
                assertEquals(202, sendMailResponse.statusCode());

                Customer customer = customerRepository.findById(customerId).orElseThrow();
                String token = customer.getEmailVerificationToken();

                String verifyPayload = """
                                {
                                  "token": "%s"
                                }
                                """.formatted(token);
                HttpResponse<String> verifyResponse = send("/api/customers/verify", "POST", verifyPayload, null, null);
                assertEquals(204, verifyResponse.statusCode());

                Customer verified = customerRepository.findById(customerId).orElseThrow();
                assertTrue(verified.isEmailVerified());
        }

        @Test
        void customerCannotAccessAnotherCustomersCartOrOrders() throws Exception {
                String suffix = UUID.randomUUID().toString().substring(0, 8);

                ensureCustomerExists("customer@example.com", "Owner Customer");

                String otherCustomerJson = """
                                {
                                  "email": "other-%s@example.com",
                                  "fullName": "Other Customer"
                                }
                                """.formatted(suffix);
                HttpResponse<String> otherResponse = send("/api/customers", "POST", otherCustomerJson, "admin",
                                "admin123");
                assertEquals(201, otherResponse.statusCode());
                Long otherCustomerId = ((Number) JsonPath.read(otherResponse.body(), "$.id")).longValue();

                String categoryJson = """
                                {
                                  "name": "SecurityTest-%s",
                                  "description": "Security ownership checks"
                                }
                                """.formatted(suffix);
                HttpResponse<String> categoryResponse = send("/api/categories", "POST", categoryJson, "admin",
                                "admin123");
                assertEquals(201, categoryResponse.statusCode());
                Long categoryId = ((Number) JsonPath.read(categoryResponse.body(), "$.id")).longValue();

                String productJson = """
                                {
                                  "name": "Secured-%s",
                                  "description": "Product for ownership test",
                                  "price": 19.99,
                                  "stockQuantity": 50,
                                  "categoryId": %d,
                                  "active": true
                                }
                                """.formatted(suffix, categoryId);
                HttpResponse<String> productResponse = send("/api/products", "POST", productJson, "admin", "admin123");
                assertEquals(201, productResponse.statusCode());
                Long productId = ((Number) JsonPath.read(productResponse.body(), "$.id")).longValue();

                String addItemJson = """
                                {
                                  "productId": %d,
                                  "quantity": 1
                                }
                                """.formatted(productId);

                HttpResponse<String> otherCartAttempt = send(
                                "/api/carts/%d/items".formatted(otherCustomerId),
                                "POST",
                                addItemJson,
                                "customer@example.com",
                                "customer123");
                assertEquals(403, otherCartAttempt.statusCode());

                String checkoutJson = """
                                {
                                  "customerId": %d
                                }
                                """.formatted(otherCustomerId);
                HttpResponse<String> otherCheckoutAttempt = send(
                                "/api/orders/checkout",
                                "POST",
                                checkoutJson,
                                "customer@example.com",
                                "customer123");
                assertEquals(403, otherCheckoutAttempt.statusCode());

                HttpResponse<String> otherOrdersListAttempt = send(
                                "/api/orders/customer/%d".formatted(otherCustomerId),
                                "GET",
                                null,
                                "customer@example.com",
                                "customer123");
                assertEquals(403, otherOrdersListAttempt.statusCode());
        }

        private HttpResponse<String> send(String path, String method, String body, String username, String password)
                        throws Exception {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + path))
                                .header("Accept", MediaType.APPLICATION_JSON_VALUE);

                if (username != null && password != null) {
                        builder.header("Authorization", "Bearer " + loginAndGetToken(username, password));
                }

                if (body != null) {
                        builder.header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                        builder.method(method, HttpRequest.BodyPublishers.ofString(body));
                } else {
                        builder.method(method, HttpRequest.BodyPublishers.noBody());
                }

                return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private String loginAndGetToken(String username, String password) throws Exception {
                String cacheKey = username + ":" + password;
                String cachedToken = tokenCache.get(cacheKey);
                if (cachedToken != null && !cachedToken.isBlank()) {
                        return cachedToken;
                }

                String payload = """
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password);

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                                .POST(HttpRequest.BodyPublishers.ofString(payload))
                                .build();

                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                String accessToken = JsonPath.read(response.body(), "$.accessToken");
                tokenCache.put(cacheKey, accessToken);
                return accessToken;
        }

        private Long ensureCustomerExists(String email, String fullName) throws Exception {
                HttpResponse<String> listResponse = send("/api/customers", "GET", null, "admin", "admin123");
                assertEquals(200, listResponse.statusCode());

                List<String> emails = JsonPath.read(listResponse.body(), "$[*].email");
                List<Object> ids = JsonPath.read(listResponse.body(), "$[*].id");

                for (int i = 0; i < emails.size(); i++) {
                        if (email.equalsIgnoreCase(emails.get(i))) {
                                return ((Number) ids.get(i)).longValue();
                        }
                }

                String createCustomerJson = """
                                {
                                  "email": "%s",
                                  "fullName": "%s"
                                }
                                """.formatted(email, fullName);
                HttpResponse<String> createResponse = send("/api/customers", "POST", createCustomerJson, "admin",
                                "admin123");
                assertEquals(201, createResponse.statusCode());
                return ((Number) JsonPath.read(createResponse.body(), "$.id")).longValue();
        }
}
