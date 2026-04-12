package com.java.ecommerce.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrder checkout(@Valid @RequestBody CheckoutRequest request) {
        return orderService.checkout(request.customerId());
    }

    @GetMapping("/{orderId}")
    public CustomerOrder get(@PathVariable Long orderId) {
        return orderService.findById(orderId);
    }

    @GetMapping("/customer/{customerId}")
    public List<CustomerOrder> listByCustomer(@PathVariable Long customerId) {
        return orderService.findByCustomer(customerId);
    }

    public record CheckoutRequest(@NotNull Long customerId) {
    }
}
