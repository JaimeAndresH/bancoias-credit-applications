package com.bancoias.creditapplications.repository.r2dbc;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import com.bancoias.creditapplications.application.port.out.CreditApplicationRepository;
import com.bancoias.creditapplications.repository.entity.CreditApplicationEntity;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class CreditApplicationPersistenceAdapter implements CreditApplicationRepository {
    private final CreditApplicationR2dbcRepository repository;

    public CreditApplicationPersistenceAdapter(CreditApplicationR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<CreditApplication> findByApplicationReference(String reference) {
        return repository.findByApplicationReference(reference).map(this::toDomain);
    }

    @Override
    public Flux<CreditApplication> findRecent(int limit) {
        return repository.findRecent(limit).map(this::toDomain);
    }

    @Override
    public Mono<CreditApplication> save(CreditApplication application) {
        return repository.save(toEntity(application)).map(this::toDomain);
    }

    private CreditApplication toDomain(CreditApplicationEntity entity) {
        return CreditApplication.restore(entity.getId(), entity.getApplicationReference(), entity.getCustomerId(),
                entity.getAmount(), entity.getTermMonths(), entity.getStatus(), entity.getRejectionReason(),
                entity.getProcessedAt(), entity.getRequestHash());
    }

    private CreditApplicationEntity toEntity(CreditApplication application) {
        return CreditApplicationEntity.builder().id(application.getId())
                .applicationReference(application.getApplicationReference()).customerId(application.getCustomerId())
                .amount(application.getAmount()).termMonths(application.getTermMonths()).status(application.getStatus())
                .rejectionReason(application.getRejectionReason()).processedAt(application.getProcessedAt())
                .requestHash(application.getRequestHash()).build();
    }
}
