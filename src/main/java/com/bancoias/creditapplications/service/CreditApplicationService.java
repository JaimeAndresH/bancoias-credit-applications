package com.bancoias.creditapplications.service;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.domain.policy.CreditApplicationPolicy;
import com.bancoias.creditapplications.domain.model.enums.RejectionReason;
import com.bancoias.creditapplications.dto.request.CreditApplicationRequest;
import com.bancoias.creditapplications.dto.response.CreditApplicationResponse;
import com.bancoias.creditapplications.exception.ApplicationReferenceConflictException;
import com.bancoias.creditapplications.exception.CreditApplicationNotFoundException;
import com.bancoias.creditapplications.mapper.CreditApplicationMapper;
import com.bancoias.creditapplications.messaging.ApprovalEventOutbox;
import com.bancoias.creditapplications.repository.CreditApplicationRepository;
import com.bancoias.creditapplications.repository.CustomerRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class CreditApplicationService {

    private final CreditApplicationMapper mapper;
    private final CreditApplicationRepository creditApplicationRepository;
    private final CustomerRepository customerRepository;
    private final TransactionalOperator transactionalOperator;
    private final ApprovalEventOutbox outbox;

    public CreditApplicationService(
            CreditApplicationMapper mapper,
            CreditApplicationRepository creditApplicationRepository,
            CustomerRepository customerRepository,
            ReactiveTransactionManager transactionManager,
            ApprovalEventOutbox outbox) {
        this.mapper = mapper;
        this.creditApplicationRepository = creditApplicationRepository;
        this.customerRepository = customerRepository;
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
        this.outbox = outbox;
    }

    public Mono<CreditApplicationResponse> process(CreditApplicationRequest request) {

        return creditApplicationRepository
            .findByApplicationReference(request.applicationReference())
                .flatMap(existing -> handleExistingApplication(existing, request))
                .switchIfEmpty(Mono.defer(() -> processNewApplication(request)));
    }

    public Mono<CreditApplicationResponse> findByApplicationReference(String applicationReference) {
        return creditApplicationRepository.findByApplicationReference(applicationReference)
                .map(mapper::toResponse)
                .switchIfEmpty(Mono.error(new CreditApplicationNotFoundException(
                        "No existe una solicitud con referencia " + applicationReference)));
    }

    public Flux<CreditApplicationResponse> findRecent(int limit) {
        if (limit < 1 || limit > 100) {
            return Flux.error(new IllegalArgumentException("limit debe estar entre 1 y 100"));
        }

        return creditApplicationRepository.findRecent(limit).map(mapper::toResponse);
    }

    private Mono<CreditApplicationResponse> processNewApplication(
            CreditApplicationRequest request) {
        return Mono.defer(() -> {
            var rejection = CreditApplicationPolicy.evaluate(request.amount(), request.termMonths());
            if (rejection.isPresent()) {
                return saveResult(request, CreditApplicationStatus.REJECTED, rejection.get());
            }

            return customerRepository.findByCustomerId(request.customerId())
                    .flatMap(customer -> {
                        if (!customer.isEligible()) {
                            return saveResult(request, CreditApplicationStatus.REJECTED,
                                    RejectionReason.CUSTOMER_BLOCKED);
                        }

                        return customerRepository.reserveCreditLimit(
                                        request.customerId(), request.amount())
                                .flatMap(updatedRows -> updatedRows == 1
                                        ? saveResult(request, CreditApplicationStatus.APPROVED, null)
                                        : saveResult(request, CreditApplicationStatus.REJECTED,
                                                RejectionReason.INSUFFICIENT_CREDIT_LIMIT));
                    })
                    .switchIfEmpty(Mono.defer(() -> saveResult(
                            request, CreditApplicationStatus.REJECTED,
                            RejectionReason.CUSTOMER_NOT_FOUND)));
        }).as(transactionalOperator::transactional)
                // Read the winning request only after the failed transaction rolls back.
                .onErrorResume(DuplicateKeyException.class, error ->
                        creditApplicationRepository
                                .findByApplicationReference(request.applicationReference())
                                .flatMap(existing -> handleExistingApplication(existing, request))
                                .switchIfEmpty(Mono.error(error)));
    }

    private Mono<CreditApplicationResponse> saveResult(
            CreditApplicationRequest request,
            CreditApplicationStatus status,
            RejectionReason rejectionReason) {
        LocalDateTime processedAt = LocalDateTime.now();
        CreditApplication application = status == CreditApplicationStatus.APPROVED
                ? CreditApplication.approve(request.applicationReference(), request.customerId(),
                        request.amount(), request.termMonths(), processedAt)
                : CreditApplication.reject(request.applicationReference(), request.customerId(),
                        request.amount(), request.termMonths(), rejectionReason, processedAt);

        return creditApplicationRepository.save(application)
                .flatMap(saved -> saved.getStatus() == CreditApplicationStatus.APPROVED
                        ? outbox.recordApproved(saved).thenReturn(saved)
                        : Mono.just(saved))
                .map(mapper::toResponse);
    }

    private Mono<CreditApplicationResponse> handleExistingApplication(
            CreditApplication existing,
            CreditApplicationRequest request) {

        boolean sameRequest = existing.matchesRequest(
                request.customerId(), request.amount(), request.termMonths());

        if (sameRequest) {
            return Mono.just(mapper.toResponse(existing));
        }

        return Mono.error(
                new ApplicationReferenceConflictException(
                        "La applicationReference ya fue procesada con datos diferentes"
                )
        );
    }
}
