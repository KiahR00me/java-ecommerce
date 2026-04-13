package com.java.ecommerce.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("select o.customer.id from CustomerOrder o where o.id = :orderId")
    Optional<Long> findCustomerIdByOrderId(@Param("orderId") Long orderId);
}
