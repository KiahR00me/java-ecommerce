package com.java.ecommerce.order;

import com.java.ecommerce.common.CustomerAccessGuard;
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
    private final CustomerAccessGuard customerAccessGuard;

    public OrderController(OrderService orderService, CustomerAccessGuard customerAccessGuard) {
        this.orderService = orderService;
        this.customerAccessGuard = customerAccessGuard;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerOrder checkout(@Valid @RequestBody CheckoutRequest request) {
        customerAccessGuard.checkCustomerAccess(request.customerId());
        return orderService.checkout(request.customerId());
    }

    @GetMapping("/{orderId}")
    public CustomerOrder get(@PathVariable Long orderId) {
        Long customerId = orderService.findCustomerIdByOrderId(orderId);
        customerAccessGuard.checkCustomerAccess(customerId);
        return orderService.findById(orderId);
    }

    @GetMapping("/customer/{customerId}")
    public List<CustomerOrder> listByCustomer(@PathVariable Long customerId) {
        customerAccessGuard.checkCustomerAccess(customerId);
        return orderService.findByCustomer(customerId);
    }

    public record CheckoutRequest(@NotNull Long customerId) {
    }
}
