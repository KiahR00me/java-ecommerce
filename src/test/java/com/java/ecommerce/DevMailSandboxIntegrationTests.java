package com.java.ecommerce;

import com.java.ecommerce.customer.Customer;
import com.java.ecommerce.customer.CustomerRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-mail-sandbox")
class DevMailSandboxIntegrationTests {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    @Autowired
    private CustomerRepository customerRepository;

    @LocalServerPort
    private int port;

    @Test
    void adminCanFetchLatestVerificationTokenFromSandboxEndpoint() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "mail-sandbox-" + suffix + "@example.com";

        String createPayload = """
                {
                  "email": "%s",
                  "fullName": "Sandbox Customer"
                }
                """.formatted(email);

        HttpResponse<String> createResponse = send("/api/customers", "POST", createPayload, "admin", "admin123");
        assertEquals(201, createResponse.statusCode());
        Long customerId = ((Number) JsonPath.read(createResponse.body(), "$.id")).longValue();

        HttpResponse<String> sendVerificationResponse = send(
                "/api/customers/%d/send-verification".formatted(customerId),
                "POST",
                null,
                "admin",
                "admin123");
        assertEquals(202, sendVerificationResponse.statusCode());

        Customer customer = customerRepository.findById(customerId).orElseThrow();
        String expectedToken = customer.getEmailVerificationToken();

        HttpResponse<String> tokenResponse = send(
                "/api/dev/mail-sandbox/customers/verification-token?email=" + email,
                "GET",
                null,
                "admin",
                "admin123");
        assertEquals(200, tokenResponse.statusCode());

        String returnedEmail = JsonPath.read(tokenResponse.body(), "$.email");
        String returnedToken = JsonPath.read(tokenResponse.body(), "$.token");
        assertEquals(email, returnedEmail);
        assertEquals(expectedToken, returnedToken);
    }

    @Test
    void customerCannotAccessSandboxEndpoint() throws Exception {
        HttpResponse<String> response = send(
                "/api/dev/mail-sandbox/customers/verification-token?email=customer@example.com",
                "GET",
                null,
                "customer@example.com",
                "customer123");
        assertEquals(403, response.statusCode());
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
}
