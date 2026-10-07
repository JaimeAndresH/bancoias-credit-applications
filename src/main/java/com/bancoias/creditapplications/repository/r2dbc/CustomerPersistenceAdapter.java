package com.bancoias.creditapplications.repository.r2dbc;

import com.bancoias.creditapplications.domain.model.Customer;
import com.bancoias.creditapplications.repository.CustomerRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;

@Repository
public class CustomerPersistenceAdapter implements CustomerRepository {
    private final CustomerR2dbcRepository repository;

    public CustomerPersistenceAdapter(CustomerR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Customer> findByCustomerId(String customerId) {
        return repository.findByCustomerId(customerId).map(entity -> Customer.builder()
                .id(entity.getId()).customerId(entity.getCustomerId()).status(entity.getStatus())
                .maxApprovedAmount(entity.getMaxApprovedAmount())
                .currentApprovedAmount(entity.getCurrentApprovedAmount()).build());
    }

    @Override
    public Mono<Integer> reserveCreditLimit(String customerId, BigDecimal amount) {
        return repository.reserveCreditLimit(customerId, amount);
    }
}
