package com.java.ecommerce;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-fast")
class ProductCursorPaginationIntegrationTests {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    @LocalServerPort
    private int port;

    @Test
    void cursorPagination_isCorrectAcrossSortModes() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = createCategory("Cursor-" + suffix);

        List<ProductFixture> fixtures = new ArrayList<>();
        fixtures.add(createProduct(categoryId, "Gamma-" + suffix, new BigDecimal("40.00")));
        fixtures.add(createProduct(categoryId, "Alpha-" + suffix, new BigDecimal("10.00")));
        fixtures.add(createProduct(categoryId, "Delta-" + suffix, new BigDecimal("30.00")));
        fixtures.add(createProduct(categoryId, "Beta-" + suffix, new BigDecimal("20.00")));
        fixtures.add(createProduct(categoryId, "Omega-" + suffix, new BigDecimal("50.00")));

        assertSortMode(fixtures, suffix, "NEWEST", "DESC", Comparator.comparingLong(ProductFixture::id).reversed());
        assertSortMode(fixtures, suffix, "PRICE", "ASC",
                Comparator.comparing(ProductFixture::price).thenComparingLong(ProductFixture::id));
        assertSortMode(fixtures, suffix, "NAME", "ASC",
                Comparator.comparing((ProductFixture p) -> p.name().toLowerCase())
                        .thenComparingLong(ProductFixture::id));
    }

    @Test
    void cursorSnapshot_isStableWhenConcurrentWritesHappen() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = createCategory("Snapshot-" + suffix);

        createProduct(categoryId, "Base-A-" + suffix, new BigDecimal("11.00"));
        createProduct(categoryId, "Base-B-" + suffix, new BigDecimal("12.00"));
        createProduct(categoryId, "Base-C-" + suffix, new BigDecimal("13.00"));
        createProduct(categoryId, "Base-D-" + suffix, new BigDecimal("14.00"));

        CursorPage firstPage = fetchCursorPage(suffix, "NEWEST", "DESC", 2, null, null);
        assertNotNull(firstPage.snapshotToken());
        assertEquals("v1", firstPage.snapshotVersion());
        assertFalse(firstPage.snapshotActive());
        assertTrue(firstPage.snapshotExpiresAtEpochMs() > firstPage.snapshotIssuedAtEpochMs());
        assertEquals(2, firstPage.ids().size());
        assertTrue(firstPage.hasNext());
        assertNotNull(firstPage.nextCursor());

        ProductFixture newProduct = createProduct(categoryId, "Base-New-" + suffix, new BigDecimal("99.00"));

        CursorPage firstPageWithSnapshot = fetchCursorPage(
                suffix,
                "NEWEST",
                "DESC",
                2,
                null,
                firstPage.snapshotToken());
        assertTrue(firstPageWithSnapshot.snapshotActive());
        assertEquals(firstPage.ids(), firstPageWithSnapshot.ids());
        assertFalse(firstPageWithSnapshot.ids().contains(newProduct.id()));

        CursorPage secondPageWithSnapshot = fetchCursorPage(
                suffix,
                "NEWEST",
                "DESC",
                2,
                firstPage.nextCursor(),
                firstPage.snapshotToken());
        assertFalse(secondPageWithSnapshot.ids().contains(newProduct.id()));

        CursorPage freshPage = fetchCursorPage(suffix, "NEWEST", "DESC", 2, null, null);
        assertTrue(freshPage.ids().contains(newProduct.id()));
    }

    @Test
    void snapshotToken_versionAndExpiryAreEnforced() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = createCategory("Snapshot-Guard-" + suffix);

        createProduct(categoryId, "Guard-A-" + suffix, new BigDecimal("21.00"));
        createProduct(categoryId, "Guard-B-" + suffix, new BigDecimal("22.00"));

        CursorPage firstPage = fetchCursorPage(suffix, "NEWEST", "DESC", 2, null, null);
        assertNotNull(firstPage.snapshotToken());

        String versionMismatchToken = rewriteSnapshotToken(firstPage.snapshotToken(), "v999", null);
        HttpResponse<String> versionResponse = send(
                "/api/products/cursor?search=" + urlEncode(suffix)
                        + "&limit=2&sortBy=NEWEST&sortDirection=DESC&snapshot=" + urlEncode(versionMismatchToken),
                "GET",
                null,
                null,
                null);
        assertEquals(400, versionResponse.statusCode());

        long expiredIssuedAt = firstPage.snapshotIssuedAtEpochMs() - (60L * 60L * 1000L);
        String expiredToken = rewriteSnapshotToken(firstPage.snapshotToken(), null, expiredIssuedAt);
        HttpResponse<String> expiredResponse = send(
                "/api/products/cursor?search=" + urlEncode(suffix)
                        + "&limit=2&sortBy=NEWEST&sortDirection=DESC&snapshot=" + urlEncode(expiredToken),
                "GET",
                null,
                null,
                null);
        assertEquals(400, expiredResponse.statusCode());
    }

    @Test
    void countEndpoint_returnsFilteredBadgesAndUpdatesAfterWrites() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long categoryId = createCategory("Counts-" + suffix);

        ProductFixture first = createProduct(categoryId, "Count-A-" + suffix, new BigDecimal("15.00"));
        createProduct(categoryId, "Count-B-" + suffix, new BigDecimal("25.00"));

        updateProductActive(categoryId, first.id(), first.name(), first.price(), false);

        HttpResponse<String> response = send(
                "/api/products/counts?search=" + urlEncode(suffix) + "&categoryId=" + categoryId,
                "GET",
                null,
                null,
                null);
        assertEquals(200, response.statusCode());

        assertEquals(2, ((Number) JsonPath.read(response.body(), "$.total")).intValue());
        assertEquals(1, ((Number) JsonPath.read(response.body(), "$.active")).intValue());
        assertEquals(1, ((Number) JsonPath.read(response.body(), "$.inactive")).intValue());
        assertFalse(((String) JsonPath.read(response.body(), "$.cacheKey")).isBlank());
    }

    private void assertSortMode(
            List<ProductFixture> fixtures,
            String suffix,
            String sortBy,
            String sortDirection,
            Comparator<ProductFixture> comparator) throws Exception {
        List<Long> expected = fixtures.stream().sorted(comparator).map(ProductFixture::id).toList();
        List<Long> actual = fetchAllIdsWithCursor(suffix, sortBy, sortDirection, 2);
        assertEquals(expected, actual);
    }

    private List<Long> fetchAllIdsWithCursor(String suffix, String sortBy, String sortDirection, int limit)
            throws Exception {
        List<Long> ids = new ArrayList<>();
        String cursor = null;
        String snapshot = null;

        for (int i = 0; i < 20; i++) {
            CursorPage page = fetchCursorPage(suffix, sortBy, sortDirection, limit, cursor, snapshot);
            if (snapshot == null) {
                snapshot = page.snapshotToken();
                assertNotNull(snapshot);
                assertFalse(snapshot.isBlank());
            } else {
                assertEquals(snapshot, page.snapshotToken());
            }

            ids.addAll(page.ids());
            if (!page.hasNext()) {
                break;
            }

            cursor = page.nextCursor();
            assertNotNull(cursor);
            assertFalse(cursor.isBlank());
        }

        return ids;
    }

    private CursorPage fetchCursorPage(
            String suffix,
            String sortBy,
            String sortDirection,
            int limit,
            String cursor,
            String snapshot) throws Exception {
        StringBuilder path = new StringBuilder("/api/products/cursor");
        path.append("?search=").append(urlEncode(suffix));
        path.append("&limit=").append(limit);
        path.append("&sortBy=").append(urlEncode(sortBy));
        path.append("&sortDirection=").append(urlEncode(sortDirection));

        if (cursor != null) {
            path.append("&cursor=").append(urlEncode(cursor));
        }

        if (snapshot != null) {
            path.append("&snapshot=").append(urlEncode(snapshot));
        }

        HttpResponse<String> response = send(path.toString(), "GET", null, null, null);
        assertEquals(200, response.statusCode());

        List<Object> idNodes = JsonPath.read(response.body(), "$.items[*].id");
        List<Long> ids = idNodes.stream().map(value -> ((Number) value).longValue()).toList();

        String nextCursor = JsonPath.read(response.body(), "$.nextCursor");
        String snapshotToken = JsonPath.read(response.body(), "$.snapshotToken");
        String snapshotVersion = JsonPath.read(response.body(), "$.snapshotVersion");
        long snapshotIssuedAtEpochMs = ((Number) JsonPath.read(response.body(), "$.snapshotIssuedAtEpochMs"))
                .longValue();
        long snapshotExpiresAtEpochMs = ((Number) JsonPath.read(response.body(), "$.snapshotExpiresAtEpochMs"))
                .longValue();
        boolean snapshotActive = JsonPath.read(response.body(), "$.snapshotActive");
        boolean hasNext = JsonPath.read(response.body(), "$.hasNext");

        return new CursorPage(ids, nextCursor, snapshotToken, snapshotVersion, snapshotIssuedAtEpochMs,
                snapshotExpiresAtEpochMs, snapshotActive, hasNext);
    }

    private String rewriteSnapshotToken(String token, String forcedVersion, Long forcedIssuedAtEpochMs) {
        String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\|", 8);
        if (parts.length != 8) {
            throw new IllegalArgumentException("Unexpected snapshot token format.");
        }

        String version = forcedVersion != null ? forcedVersion : parts[0];
        String maxId = parts[1];
        String issuedAt = forcedIssuedAtEpochMs != null ? String.valueOf(forcedIssuedAtEpochMs) : parts[2];
        String sortBy = parts[3];
        String sortDirection = parts[4];
        String category = URLDecoder.decode(parts[5], StandardCharsets.UTF_8);
        String active = URLDecoder.decode(parts[6], StandardCharsets.UTF_8);
        String search = URLDecoder.decode(parts[7], StandardCharsets.UTF_8);

        String raw = version + "|"
                + maxId + "|"
                + issuedAt + "|"
                + sortBy + "|"
                + sortDirection + "|"
                + URLEncoder.encode(category, StandardCharsets.UTF_8) + "|"
                + URLEncoder.encode(active, StandardCharsets.UTF_8) + "|"
                + URLEncoder.encode(search, StandardCharsets.UTF_8);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private long createCategory(String name) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "description": "Cursor test category"
                }
                """.formatted(name);

        HttpResponse<String> response = send("/api/categories", "POST", payload, "admin", "admin123");
        assertEquals(201, response.statusCode());
        return ((Number) JsonPath.read(response.body(), "$.id")).longValue();
    }

    private ProductFixture createProduct(long categoryId, String name, BigDecimal price) throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "description": "Cursor integration test product %s",
                  "imageUrl": "https://example.com/%s.png",
                  "price": %s,
                  "stockQuantity": 50,
                  "categoryId": %d,
                  "active": true
                }
                """.formatted(name, name, name.toLowerCase().replace(" ", "-"), price.toPlainString(), categoryId);

        HttpResponse<String> response = send("/api/products", "POST", payload, "admin", "admin123");
        assertEquals(201, response.statusCode());

        long id = ((Number) JsonPath.read(response.body(), "$.id")).longValue();
        BigDecimal resolvedPrice = new BigDecimal(JsonPath.read(response.body(), "$.price").toString());
        String resolvedName = JsonPath.read(response.body(), "$.name");
        return new ProductFixture(id, resolvedName, resolvedPrice);
    }

    private void updateProductActive(long categoryId, long productId, String name, BigDecimal price, boolean active)
            throws Exception {
        String payload = """
                {
                  "name": "%s",
                  "description": "Updated %s",
                  "imageUrl": "https://example.com/%s.png",
                  "price": %s,
                  "stockQuantity": 50,
                  "categoryId": %d,
                  "active": %s
                }
                """.formatted(name, name, name.toLowerCase().replace(" ", "-"), price.toPlainString(), categoryId,
                Boolean.toString(active));

        HttpResponse<String> response = send(
                "/api/products/" + productId,
                "PUT",
                payload,
                "admin",
                "admin123");
        assertEquals(200, response.statusCode());
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

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ProductFixture(long id, String name, BigDecimal price) {
    }

    private record CursorPage(
            List<Long> ids,
            String nextCursor,
            String snapshotToken,
            String snapshotVersion,
            long snapshotIssuedAtEpochMs,
            long snapshotExpiresAtEpochMs,
            boolean snapshotActive,
            boolean hasNext) {
    }
}
