package com.java.ecommerce.customer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmailIgnoreCase(String email);

    Optional<Customer> findByEmailVerificationToken(String token);

    boolean existsByEmailIgnoreCase(String email);
}
