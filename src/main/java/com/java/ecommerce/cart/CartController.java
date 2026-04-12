package com.java.ecommerce.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts/{customerId}")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Cart getCart(@PathVariable Long customerId) {
        return cartService.getOrCreateCart(customerId);
    }

    @PostMapping("/items")
    public Cart addItem(@PathVariable Long customerId, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(customerId, request.productId(), request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public Cart removeItem(@PathVariable Long customerId, @PathVariable Long productId) {
        return cartService.removeItem(customerId, productId);
    }

    @DeleteMapping
    public void clear(@PathVariable Long customerId) {
        cartService.clear(customerId);
    }

    public record AddCartItemRequest(
            @NotNull Long productId,
            @NotNull @Positive Integer quantity) {
    }
}
