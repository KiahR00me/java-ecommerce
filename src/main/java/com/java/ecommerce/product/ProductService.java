package com.java.ecommerce.product;

import com.java.ecommerce.category.Category;
import com.java.ecommerce.category.CategoryRepository;
import com.java.ecommerce.common.BusinessException;
import com.java.ecommerce.common.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ProductService {

    private static final int DEFAULT_CURSOR_LIMIT = 8;
    private static final int MAX_CURSOR_LIMIT = 40;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;
    private final String snapshotTokenVersion;
    private final long snapshotTtlMillis;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
            EntityManager entityManager,
            @Value("${app.pagination.snapshot.version:v1}") String snapshotTokenVersion,
            @Value("${app.pagination.snapshot.ttl-seconds:300}") long snapshotTtlSeconds) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager;
        this.snapshotTokenVersion = snapshotTokenVersion;
        this.snapshotTtlMillis = Math.max(1, snapshotTtlSeconds) * 1000L;
    }

    public Page<Product> search(String search, Long categoryId, Boolean active, Pageable pageable) {
        String normalizedSearch = normalizeSearch(search);
        return productRepository.search(normalizedSearch, categoryId, active, pageable);
    }

    public ProductCursorPage searchWithCursor(
            String search,
            Long categoryId,
            Boolean active,
            String cursor,
            String snapshotToken,
            int limit,
            ProductSortBy sortBy,
            Sort.Direction sortDirection) {
        int normalizedLimit = normalizeLimit(limit);
        ProductSortBy normalizedSortBy = sortBy == null ? ProductSortBy.NEWEST : sortBy;
        Sort.Direction normalizedDirection = sortDirection == null ? Sort.Direction.DESC : sortDirection;
        String normalizedSearch = normalizeSearch(search);

        SnapshotDescriptor snapshot = resolveSnapshot(
                snapshotToken,
                normalizedSearch,
                categoryId,
                active,
                normalizedSortBy,
                normalizedDirection);

        DecodedCursor decodedCursor = decodeCursor(cursor, normalizedSortBy, normalizedDirection);
        List<Product> rows = findByCursor(normalizedSearch, categoryId, active, snapshot.maxId(), decodedCursor,
                normalizedLimit + 1,
                normalizedSortBy, normalizedDirection);

        boolean hasNext = rows.size() > normalizedLimit;
        if (hasNext) {
            rows = rows.subList(0, normalizedLimit);
        }

        String nextCursor = null;
        if (hasNext && !rows.isEmpty()) {
            nextCursor = encodeCursor(rows.get(rows.size() - 1), normalizedSortBy, normalizedDirection);
        }

        return new ProductCursorPage(rows, nextCursor, snapshot.token(), snapshotTokenVersion,
                snapshot.issuedAtEpochMs(), snapshot.expiresAtEpochMs(), snapshot.active(), hasNext, normalizedLimit,
                normalizedSortBy,
                normalizedDirection.name());
    }

    @Cacheable(value = "productCounts", key = "#root.target.buildCountCacheKey(#search, #categoryId)")
    public ProductCountResponse countProducts(String search, Long categoryId) {
        String normalizedSearch = normalizeSearch(search);
        ProductCountProjection counts = productRepository.countByFilter(normalizedSearch, categoryId);
        long total = counts != null && counts.getTotalCount() != null ? counts.getTotalCount() : 0L;
        long activeCount = counts != null && counts.getActiveCount() != null ? counts.getActiveCount() : 0L;
        long inactiveCount = counts != null && counts.getInactiveCount() != null ? counts.getInactiveCount() : 0L;
        return new ProductCountResponse(total, activeCount, inactiveCount,
                buildCountCacheKey(normalizedSearch, categoryId));
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    @Transactional
    @CacheEvict(value = "productCounts", allEntries = true)
    public Product create(String name, String description, String imageUrl, BigDecimal price, Integer stockQuantity,
            Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setImageUrl(imageUrl);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);
        product.setCategory(category);
        return productRepository.save(product);
    }

    @Transactional
    @CacheEvict(value = "productCounts", allEntries = true)
    public Product update(Long id, String name, String description, String imageUrl, BigDecimal price,
            Integer stockQuantity,
            Long categoryId, boolean active) {
        Product product = findById(id);
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + categoryId));
        product.setName(name);
        product.setDescription(description);
        product.setImageUrl(imageUrl);
        product.setPrice(price);
        product.setStockQuantity(stockQuantity);
        product.setCategory(category);
        product.setActive(active);
        return product;
    }

    @Transactional
    @CacheEvict(value = "productCounts", allEntries = true)
    public void delete(Long id) {
        productRepository.delete(findById(id));
    }

    public String buildCountCacheKey(String search, Long categoryId) {
        String normalizedSearch = normalizeSearch(search);
        return "search=" + (normalizedSearch == null ? "*" : normalizedSearch)
                + "|category=" + (categoryId == null ? "*" : categoryId);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_CURSOR_LIMIT;
        }
        return Math.min(limit, MAX_CURSOR_LIMIT);
    }

    private List<Product> findByCursor(
            String search,
            Long categoryId,
            Boolean active,
            long snapshotMaxId,
            DecodedCursor cursor,
            int maxResults,
            ProductSortBy sortBy,
            Sort.Direction direction) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Product> cq = cb.createQuery(Product.class);
        Root<Product> root = cq.from(Product.class);
        Join<Product, Category> category = root.join("category");

        List<Predicate> predicates = new ArrayList<>();

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        if (normalizedSearch != null) {
            String like = "%" + normalizedSearch + "%";
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                    cb.like(cb.lower(category.get("name")), like)));
        }

        if (categoryId != null) {
            predicates.add(cb.equal(category.get("id"), categoryId));
        }

        if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
        }

        predicates.add(cb.lessThanOrEqualTo(root.get("id"), snapshotMaxId));

        if (cursor != null) {
            predicates.add(buildCursorPredicate(cb, root, cursor, sortBy, direction));
        }

        List<Order> orders = buildOrders(cb, root, sortBy, direction);
        cq.select(root)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(orders);

        TypedQuery<Product> query = entityManager.createQuery(cq);
        query.setMaxResults(maxResults);
        return query.getResultList();
    }

    private SnapshotDescriptor resolveSnapshot(
            String snapshotToken,
            String normalizedSearch,
            Long categoryId,
            Boolean active,
            ProductSortBy sortBy,
            Sort.Direction direction) {
        long maxId = productRepository.findMaxId().orElse(0L);
        long now = nowEpochMillis();

        if (snapshotToken == null || snapshotToken.isBlank()) {
            long issuedAt = now;
            long expiresAt = issuedAt + snapshotTtlMillis;
            String token = encodeSnapshotToken(maxId, issuedAt, normalizedSearch, categoryId, active, sortBy,
                    direction);
            return new SnapshotDescriptor(token, maxId, issuedAt, expiresAt, false);
        }

        DecodedSnapshot decoded = decodeSnapshotToken(snapshotToken);

        if (decoded.maxId() < 0) {
            throw new BusinessException("Invalid snapshot token.");
        }

        if (!decoded.version().equals(snapshotTokenVersion)) {
            throw new BusinessException("Snapshot token version mismatch.");
        }

        if (!decoded.sortBy().equals(sortBy.name()) || !decoded.sortDirection().equals(direction.name())) {
            throw new BusinessException("Snapshot token does not match sort options.");
        }

        long expiresAt = decoded.issuedAtEpochMs() + snapshotTtlMillis;
        if (now > expiresAt) {
            throw new BusinessException("Snapshot token expired. Restart cursor pagination without snapshot.");
        }

        String expectedSearch = normalizedSearch == null ? "" : normalizedSearch;
        String expectedCategory = categoryId == null ? "" : categoryId.toString();
        String expectedActive = active == null ? "" : active.toString();

        if (!decoded.search().equals(expectedSearch)
                || !decoded.categoryId().equals(expectedCategory)
                || !decoded.active().equals(expectedActive)) {
            throw new BusinessException("Snapshot token does not match filters.");
        }

        return new SnapshotDescriptor(snapshotToken, decoded.maxId(), decoded.issuedAtEpochMs(), expiresAt, true);
    }

    private String encodeSnapshotToken(
            long maxId,
            long issuedAtEpochMs,
            String search,
            Long categoryId,
            Boolean active,
            ProductSortBy sortBy,
            Sort.Direction direction) {
        String normalizedSearch = search == null ? "" : search;
        String normalizedCategoryId = categoryId == null ? "" : categoryId.toString();
        String normalizedActive = active == null ? "" : active.toString();

        String raw = snapshotTokenVersion + "|"
                + maxId + "|"
                + issuedAtEpochMs + "|"
                + sortBy.name() + "|"
                + direction.name() + "|"
                + URLEncoder.encode(normalizedCategoryId, StandardCharsets.UTF_8) + "|"
                + URLEncoder.encode(normalizedActive, StandardCharsets.UTF_8) + "|"
                + URLEncoder.encode(normalizedSearch, StandardCharsets.UTF_8);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private DecodedSnapshot decodeSnapshotToken(String snapshotToken) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(snapshotToken), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 8);

            if (parts.length != 8) {
                throw new BusinessException("Invalid snapshot token.");
            }

            String version = parts[0];
            long maxId = Long.parseLong(parts[1]);
            long issuedAtEpochMs = Long.parseLong(parts[2]);
            String sortBy = parts[3];
            String sortDirection = parts[4];
            String categoryId = URLDecoder.decode(parts[5], StandardCharsets.UTF_8);
            String active = URLDecoder.decode(parts[6], StandardCharsets.UTF_8);
            String search = URLDecoder.decode(parts[7], StandardCharsets.UTF_8);

            return new DecodedSnapshot(version, maxId, issuedAtEpochMs, sortBy, sortDirection, categoryId, active,
                    search);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid snapshot token.");
        }
    }

    private Predicate buildCursorPredicate(
            CriteriaBuilder cb,
            Root<Product> root,
            DecodedCursor cursor,
            ProductSortBy sortBy,
            Sort.Direction direction) {
        boolean asc = direction.isAscending();
        Path<Long> idPath = root.get("id");

        return switch (sortBy) {
            case NEWEST -> asc
                    ? cb.greaterThan(idPath, cursor.id())
                    : cb.lessThan(idPath, cursor.id());
            case PRICE -> {
                Path<BigDecimal> pricePath = root.get("price");
                BigDecimal cursorPrice;
                try {
                    cursorPrice = new BigDecimal(cursor.value());
                } catch (NumberFormatException ex) {
                    throw new BusinessException("Invalid cursor for PRICE sort.");
                }

                Predicate priceComparison = asc
                        ? cb.greaterThan(pricePath, cursorPrice)
                        : cb.lessThan(pricePath, cursorPrice);
                Predicate tieBreaker = cb.and(
                        cb.equal(pricePath, cursorPrice),
                        asc ? cb.greaterThan(idPath, cursor.id()) : cb.lessThan(idPath, cursor.id()));
                yield cb.or(priceComparison, tieBreaker);
            }
            case NAME -> {
                Expression<String> normalizedName = cb.lower(root.get("name"));
                String cursorName = cursor.value();

                Predicate nameComparison = asc
                        ? cb.greaterThan(normalizedName, cursorName)
                        : cb.lessThan(normalizedName, cursorName);
                Predicate tieBreaker = cb.and(
                        cb.equal(normalizedName, cursorName),
                        asc ? cb.greaterThan(idPath, cursor.id()) : cb.lessThan(idPath, cursor.id()));
                yield cb.or(nameComparison, tieBreaker);
            }
        };
    }

    private List<Order> buildOrders(
            CriteriaBuilder cb,
            Root<Product> root,
            ProductSortBy sortBy,
            Sort.Direction direction) {
        boolean asc = direction.isAscending();
        Path<Long> idPath = root.get("id");

        return switch (sortBy) {
            case NEWEST -> List.of(asc ? cb.asc(idPath) : cb.desc(idPath));
            case PRICE -> {
                Path<BigDecimal> pricePath = root.get("price");
                yield List.of(
                        asc ? cb.asc(pricePath) : cb.desc(pricePath),
                        asc ? cb.asc(idPath) : cb.desc(idPath));
            }
            case NAME -> {
                Expression<String> normalizedName = cb.lower(root.get("name"));
                yield List.of(
                        asc ? cb.asc(normalizedName) : cb.desc(normalizedName),
                        asc ? cb.asc(idPath) : cb.desc(idPath));
            }
        };
    }

    private String encodeCursor(Product product, ProductSortBy sortBy, Sort.Direction direction) {
        String value = switch (sortBy) {
            case NEWEST -> String.valueOf(product.getId());
            case PRICE -> product.getPrice().toPlainString();
            case NAME -> normalizeName(product.getName());
        };

        String raw = sortBy.name() + "|" + direction.name() + "|"
                + URLEncoder.encode(value, StandardCharsets.UTF_8) + "|" + product.getId();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private DecodedCursor decodeCursor(String rawCursor, ProductSortBy sortBy, Sort.Direction direction) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 4);

            if (parts.length != 4) {
                throw new BusinessException("Invalid cursor format.");
            }

            if (!parts[0].equals(sortBy.name()) || !parts[1].equals(direction.name())) {
                throw new BusinessException("Cursor does not match the requested sort options.");
            }

            String value = URLDecoder.decode(parts[2], StandardCharsets.UTF_8);
            long id = Long.parseLong(parts[3]);
            return new DecodedCursor(value, id);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid cursor token.");
        }
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase();
    }

    private String normalizeSearch(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }

    private long nowEpochMillis() {
        return Instant.now().toEpochMilli();
    }

    private record SnapshotDescriptor(
            String token,
            long maxId,
            long issuedAtEpochMs,
            long expiresAtEpochMs,
            boolean active) {
    }

    private record DecodedSnapshot(
            String version,
            long maxId,
            long issuedAtEpochMs,
            String sortBy,
            String sortDirection,
            String categoryId,
            String active,
            String search) {
    }

    private record DecodedCursor(String value, long id) {
    }
}
