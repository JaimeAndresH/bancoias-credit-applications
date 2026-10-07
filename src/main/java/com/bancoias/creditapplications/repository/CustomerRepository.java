package com.bancoias.creditapplications.repository;

import com.bancoias.creditapplications.domain.model.Customer;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;

/** Application persistence port. */
public interface CustomerRepository {
    Mono<Customer> findByCustomerId(String customerId);
    Mono<Integer> reserveCreditLimit(String customerId, BigDecimal amount);
}
