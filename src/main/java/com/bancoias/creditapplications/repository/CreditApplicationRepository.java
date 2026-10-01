package com.bancoias.creditapplications.repository;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditApplicationRepository
        extends ReactiveCrudRepository<CreditApplication, Long> {

    Mono<CreditApplication> findByApplicationReference(String applicationReference);

    @Query("""
            SELECT * FROM credit_application
            ORDER BY processed_at DESC, id DESC
            LIMIT :limit
            """)
    Flux<CreditApplication> findRecent(int limit);
}
