package com.java.ecommerce;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-fast")
class ApiLandingIntegrationTests {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void rootServesStaticLandingPage() throws Exception {
        HttpResponse<String> response = send("/", MediaType.TEXT_HTML_VALUE);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Ecommerce API"));
        assertTrue(response.body().contains("/api/products/cursor"));
    }

    @Test
    void apiRootReturnsPublicJsonMessage() throws Exception {
        HttpResponse<String> response = send("/api", MediaType.APPLICATION_JSON_VALUE);

        assertEquals(200, response.statusCode());
        assertEquals("ecommerce-api", JsonPath.read(response.body(), "$.service"));
        assertEquals("ok", JsonPath.read(response.body(), "$.status"));
        assertTrue(((String) JsonPath.read(response.body(), "$.message")).contains("/api/products/cursor"));
    }

    private HttpResponse<String> send(String path, String acceptHeader) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Accept", acceptHeader)
                .GET()
                .build();

        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
