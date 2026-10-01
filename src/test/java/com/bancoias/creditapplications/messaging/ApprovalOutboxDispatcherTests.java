package com.bancoias.creditapplications.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.ReactiveTransaction;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalOutboxDispatcherTests {

    private ApprovalEventOutbox outbox;
    private RabbitApprovalPublisher publisher;
    private ReactiveTransactionManager manager;
    private ReactiveTransaction transaction;
    private ApprovalOutboxDispatcher dispatcher;
    private ApprovalEventOutbox.PendingEvent event;

    @BeforeEach
    void setUp() {
        outbox = mock(ApprovalEventOutbox.class);
        publisher = mock(RabbitApprovalPublisher.class);
        manager = mock(ReactiveTransactionManager.class);
        transaction = mock(ReactiveTransaction.class);
        event = new ApprovalEventOutbox.PendingEvent(UUID.randomUUID(), "{}");
        when(manager.getReactiveTransaction(any())).thenReturn(Mono.just(transaction));
        when(manager.commit(transaction)).thenReturn(Mono.empty());
        when(manager.rollback(transaction)).thenReturn(Mono.empty());
        when(outbox.lockPending()).thenReturn(Flux.just(event));
        when(outbox.markPublished(event.eventId())).thenReturn(Mono.empty());
        dispatcher = new ApprovalOutboxDispatcher(outbox, publisher, manager);
    }

    @Test
    void marksPublishedOnlyAfterSuccessfulPublish() {
        when(publisher.publish(event)).thenReturn(Mono.empty());

        StepVerifier.create(dispatcher.dispatchPending()).verifyComplete();

        var order = inOrder(publisher, outbox, manager);
        order.verify(manager).getReactiveTransaction(any());
        order.verify(outbox).lockPending();
        order.verify(publisher).publish(event);
        order.verify(outbox).markPublished(event.eventId());
        order.verify(manager).commit(transaction);
    }

    @Test
    void keepsEventPendingOnFailureAndRetriesNextSubscription() {
        when(publisher.publish(event)).thenReturn(
                Mono.error(new IllegalStateException("RabbitMQ unavailable")), Mono.empty());
        Mono<Void> scheduledPublisher = dispatcher.dispatchPending();

        StepVerifier.create(scheduledPublisher).verifyComplete();
        verify(outbox, never()).markPublished(any());
        verify(manager).rollback(transaction);

        StepVerifier.create(scheduledPublisher).verifyComplete();
        verify(outbox).markPublished(event.eventId());
        verify(manager).commit(transaction);
    }
}
