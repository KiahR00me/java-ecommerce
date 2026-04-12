package com.java.ecommerce.order;

import com.java.ecommerce.cart.Cart;
import com.java.ecommerce.cart.CartItem;
import com.java.ecommerce.cart.CartService;
import com.java.ecommerce.common.BusinessException;
import com.java.ecommerce.common.ResourceNotFoundException;
import com.java.ecommerce.customer.Customer;
import com.java.ecommerce.customer.CustomerRepository;
import com.java.ecommerce.product.Product;
import com.java.ecommerce.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final CartService cartService;

    public OrderService(
            CustomerOrderRepository orderRepository,
            CustomerRepository customerRepository,
            ProductRepository productRepository,
            CartService cartService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.cartService = cartService;
    }

    public List<CustomerOrder> findByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public CustomerOrder findById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    @Transactional
    public CustomerOrder checkout(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        Cart cart = cartService.getOrCreateCart(customerId);
        if (cart.getItems().isEmpty()) {
            throw new BusinessException("Cannot checkout an empty cart");
        }

        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = productRepository.findById(cartItem.getProduct().getId())
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Product not found: " + cartItem.getProduct().getId()));

            if (product.getStockQuantity() < cartItem.getQuantity()) {
                throw new BusinessException("Insufficient stock for product: " + product.getId());
            }

            product.setStockQuantity(product.getStockQuantity() - cartItem.getQuantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(product.getPrice());
            order.getItems().add(orderItem);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity())));
        }

        order.setTotalAmount(total);
        CustomerOrder saved = orderRepository.save(order);
        cartService.clear(customerId);
        return saved;
    }
}
