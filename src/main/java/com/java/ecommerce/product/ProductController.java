package com.java.ecommerce.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/cursor")
    public ProductCursorPage listWithCursor(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String snapshot,
            @RequestParam(defaultValue = "8") int limit,
            @RequestParam(defaultValue = "NEWEST") ProductSortBy sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction sortDirection) {
        return productService.searchWithCursor(search, categoryId, active, cursor, snapshot, limit, sortBy,
                sortDirection);
    }

    @GetMapping("/counts")
    public ProductCountResponse counts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId) {
        return productService.countProducts(search, categoryId);
    }

    @GetMapping
    public Page<Product> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 8, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return productService.search(search, categoryId, active, pageable);
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product create(@Valid @RequestBody ProductRequest request) {
        return productService.create(
                request.name(),
                request.description(),
                request.imageUrl(),
                request.price(),
                request.stockQuantity(),
                request.categoryId());
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(
                id,
                request.name(),
                request.description(),
                request.imageUrl(),
                request.price(),
                request.stockQuantity(),
                request.categoryId(),
                request.active());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    public record ProductRequest(
            @NotBlank String name,
            String description,
            @Size(max = 1000) String imageUrl,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @NotNull @Positive Integer stockQuantity,
            @NotNull Long categoryId,
            boolean active) {
    }
}
