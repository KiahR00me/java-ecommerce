package com.java.ecommerce.customer;

import com.java.ecommerce.common.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerService customerService;

    public CustomerController(CustomerRepository customerRepository, CustomerService customerService) {
        this.customerRepository = customerRepository;
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerView create(@Valid @RequestBody CustomerRequest request) {
        return CustomerView.from(customerService.createCustomer(request.email(), request.fullName()));
    }

    @PostMapping("/{id}/send-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendVerificationEmail(@PathVariable Long id) {
        customerService.sendVerificationEmail(id);
    }

    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerificationRequest request) {
        customerService.verifyByToken(request.token());
    }

    @GetMapping
    public List<CustomerView> list() {
        return customerRepository.findAll().stream().map(CustomerView::from).toList();
    }

    @GetMapping("/me")
    public CustomerView me(Authentication authentication) {
        return CustomerView.from(customerService.findByEmail(authentication.getName()));
    }

    @GetMapping("/{id}")
    public CustomerView get(@PathVariable Long id) {
        return CustomerView.from(customerService.findById(id));
    }

    public record CustomerRequest(
            @NotBlank @Email String email,
            @NotBlank String fullName) {
    }

    public record VerificationRequest(@NotBlank String token) {
    }

    public record CustomerView(Long id, String email, String fullName, boolean emailVerified) {
        static CustomerView from(Customer customer) {
            return new CustomerView(
                    customer.getId(),
                    customer.getEmail(),
                    customer.getFullName(),
                    customer.isEmailVerified());
        }
    }
}
