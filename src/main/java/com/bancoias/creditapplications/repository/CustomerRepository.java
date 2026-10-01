package com.bancoias.creditapplications.repository;

import com.bancoias.creditapplications.domain.model.Customer;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface CustomerRepository
        extends ReactiveCrudRepository<Customer, Long> {

    Mono<Customer> findByCustomerId(String customerId);

    @Modifying
    @Query("""
            UPDATE customer
            SET current_approved_amount = current_approved_amount + :amount
            WHERE customer_id = :customerId
              AND status = 'ELIGIBLE'
              AND current_approved_amount + :amount <= max_approved_amount
        """)
    Mono<Integer> reserveCreditLimit(String customerId, BigDecimal amount);
}
