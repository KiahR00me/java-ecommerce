package com.java.ecommerce.cart;

import com.java.ecommerce.common.BusinessException;
import com.java.ecommerce.common.ResourceNotFoundException;
import com.java.ecommerce.customer.Customer;
import com.java.ecommerce.customer.CustomerRepository;
import com.java.ecommerce.product.Product;
import com.java.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository, CustomerRepository customerRepository,
            ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Cart getOrCreateCart(Long customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    Customer customer = customerRepository.findById(customerId)
                            .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
                    Cart cart = new Cart();
                    cart.setCustomer(customer);
                    return cartRepository.save(cart);
                });
    }

    @Transactional
    public Cart addItem(Long customerId, Long productId, Integer quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantity must be greater than zero");
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        if (product.getStockQuantity() < quantity) {
            throw new BusinessException("Insufficient stock for product: " + productId);
        }

        Cart cart = getOrCreateCart(customerId);
        CartItem existing = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst()
                .orElse(null);

        if (existing == null) {
            CartItem item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(quantity);
            cart.getItems().add(item);
        } else {
            int nextQuantity = existing.getQuantity() + quantity;
            if (product.getStockQuantity() < nextQuantity) {
                throw new BusinessException("Insufficient stock for product: " + productId);
            }
            existing.setQuantity(nextQuantity);
        }
        cart.touch();
        return cart;
    }

    @Transactional
    public Cart removeItem(Long customerId, Long productId) {
        Cart cart = getOrCreateCart(customerId);
        boolean removed = cart.getItems().removeIf(item -> item.getProduct().getId().equals(productId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found for product: " + productId);
        }
        cart.touch();
        return cart;
    }

    @Transactional
    public void clear(Long customerId) {
        Cart cart = getOrCreateCart(customerId);
        cart.getItems().clear();
        cart.touch();
    }
}
