package com.bancoias.creditapplications.repository.r2dbc;

import com.bancoias.creditapplications.repository.entity.CreditApplicationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditApplicationR2dbcRepository
        extends ReactiveCrudRepository<CreditApplicationEntity, Long> {

    Mono<CreditApplicationEntity> findByApplicationReference(String applicationReference);

    @Query("""
            SELECT * FROM credit_application
            ORDER BY processed_at DESC, id DESC
            LIMIT :limit
            """)
    Flux<CreditApplicationEntity> findRecent(int limit);
}
