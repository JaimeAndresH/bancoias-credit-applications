package com.bancoias.creditapplications.repository;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

/** Application persistence port. */
public interface CreditApplicationRepository {
    Mono<CreditApplication> findByApplicationReference(String reference);
    Flux<CreditApplication> findRecent(int limit);
    Mono<CreditApplication> save(CreditApplication application);
}
