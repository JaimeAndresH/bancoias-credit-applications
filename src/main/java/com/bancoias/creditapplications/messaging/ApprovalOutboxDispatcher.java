package com.bancoias.creditapplications.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "bancoias.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class ApprovalOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ApprovalOutboxDispatcher.class);
    private final ApprovalEventOutbox outbox;
    private final RabbitApprovalPublisher publisher;
    private final TransactionalOperator transaction;

    public ApprovalOutboxDispatcher(ApprovalEventOutbox outbox, RabbitApprovalPublisher publisher,
                                   ReactiveTransactionManager transactionManager) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.transaction = TransactionalOperator.create(transactionManager);
    }

    @Scheduled(fixedDelayString = "${bancoias.messaging.poll-delay-ms:5000}", initialDelay = 5000)
    public Mono<Void> dispatchPending() {
        return Mono.defer(() -> outbox.lockPending().collectList()
                        .flatMapMany(Flux::fromIterable)
                        .concatMap(event -> publisher.publish(event)
                                .then(Mono.defer(() -> outbox.markPublished(event.eventId()))))
                        .then())
                .as(transaction::transactional)
                .onErrorResume(error -> {
                    log.warn("Publicacion pendiente; se reintentara en el siguiente ciclo", error);
                    return Mono.empty();
                });
    }
}
