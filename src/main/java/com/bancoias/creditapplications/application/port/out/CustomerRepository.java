package com.bancoias.creditapplications.application.port.out;

import com.bancoias.creditapplications.domain.model.Customer;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;

/** Application persistence port. */
public interface CustomerRepository {
    Mono<Customer> findByCustomerId(String customerId);
    Mono<Integer> reserveCreditLimit(String customerId, BigDecimal amount);
}
