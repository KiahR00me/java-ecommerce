package com.java.ecommerce.common;

import com.java.ecommerce.customer.Customer;
import com.java.ecommerce.customer.CustomerRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CustomerAccessGuard {

    private final CustomerRepository customerRepository;

    public CustomerAccessGuard(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public void checkCustomerAccess(Long customerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) {
            return;
        }

        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()));
        if (!isCustomer) {
            throw new AccessDeniedException("Access denied");
        }

        String principalName = authentication.getName();
        Customer customer = customerRepository.findByEmailIgnoreCase(principalName)
                .orElseThrow(() -> new AccessDeniedException("Customer principal is not mapped to a customer record"));

        if (!customer.getId().equals(customerId)) {
            throw new AccessDeniedException("You can only access your own resources");
        }
    }
}