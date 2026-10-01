package com.bancoias.creditapplications.messaging;

import com.bancoias.creditapplications.domain.model.CreditApplication;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Repository
public class ApprovalEventOutbox {

    private final DatabaseClient database;
    private final ObjectMapper mapper;

    public ApprovalEventOutbox(DatabaseClient database, ObjectMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public Mono<Void> recordApproved(CreditApplication application) {
        return Mono.defer(() -> {
            CreditApprovedEvent event = new CreditApprovedEvent(UUID.randomUUID(),
                    "CreditApplicationApproved", 1, application.getApplicationReference(),
                    application.getCustomerId(), application.getAmount(), application.getTermMonths(),
                    application.getProcessedAt());
            return database.sql("""
                            INSERT INTO credit_approval_outbox (event_id, application_reference, payload)
                            VALUES (:eventId, :reference, :payload)
                            """)
                    .bind("eventId", event.eventId())
                    .bind("reference", event.applicationReference())
                    .bind("payload", mapper.writeValueAsString(event))
                    .fetch().rowsUpdated().then();
        });
    }

    public Flux<PendingEvent> lockPending() {
        return database.sql("""
                        SELECT event_id, payload FROM credit_approval_outbox
                        WHERE published_at IS NULL
                        ORDER BY created_at, event_id
                        LIMIT 10
                        FOR UPDATE SKIP LOCKED
                        """)
                .map((row, metadata) -> new PendingEvent(
                        row.get("event_id", UUID.class), row.get("payload", String.class)))
                .all();
    }

    public Mono<Void> markPublished(UUID eventId) {
        return database.sql("""
                        UPDATE credit_approval_outbox SET published_at = CURRENT_TIMESTAMP
                        WHERE event_id = :eventId
                        """)
                .bind("eventId", eventId).fetch().rowsUpdated().then();
    }

    public record PendingEvent(UUID eventId, String payload) {
    }
}
