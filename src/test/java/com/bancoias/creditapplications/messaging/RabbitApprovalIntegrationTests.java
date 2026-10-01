package com.bancoias.creditapplications.messaging;

import com.bancoias.creditapplications.config.RabbitMessagingConfiguration;
import com.bancoias.creditapplications.domain.model.enums.CreditApplicationStatus;
import com.bancoias.creditapplications.dto.request.CreditApplicationRequest;
import com.bancoias.creditapplications.service.CreditApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "rabbitmq.integration", matches = "true")
@SpringBootTest(properties = {
        "bancoias.messaging.enabled=false",
        "spring.r2dbc.url=r2dbc:postgresql://localhost:5432/bancoias_messaging_test"
})
class RabbitApprovalIntegrationTests {

    @Autowired private CreditApplicationService service;
    @Autowired private ApprovalEventOutbox outbox;
    @Autowired private RabbitApprovalPublisher publisher;
    @Autowired private ReactiveTransactionManager transactionManager;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private DatabaseClient database;
    @Autowired private ObjectMapper mapper;

    private RabbitAdmin admin;
    private String queueName;
    private String reference;
    private String customerId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        reference = "IT-" + suffix;
        customerId = "IT-" + suffix;
        queueName = "bancoias.test." + suffix;
        admin = new RabbitAdmin(rabbit.getConnectionFactory());
        TopicExchange exchange = new TopicExchange(RabbitMessagingConfiguration.EXCHANGE, true, false);
        Queue queue = new Queue(queueName, true, false, false);
        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(exchange)
                .with(RabbitMessagingConfiguration.ROUTING_KEY));
        database.sql("""
                        INSERT INTO customer (customer_id, status, max_approved_amount, current_approved_amount)
                        VALUES (:customer, 'ELIGIBLE', 1000, 0)
                        """)
                .bind("customer", customerId).fetch().rowsUpdated().block(Duration.ofSeconds(10));
    }

    @AfterEach
    void cleanUp() {
        database.sql("DELETE FROM credit_approval_outbox WHERE application_reference = :reference")
                .bind("reference", reference).fetch().rowsUpdated()
                .then(database.sql("DELETE FROM credit_application WHERE application_reference = :reference")
                        .bind("reference", reference).fetch().rowsUpdated())
                .then(database.sql("DELETE FROM customer WHERE customer_id = :customer")
                        .bind("customer", customerId).fetch().rowsUpdated())
                .block(Duration.ofSeconds(10));
        admin.deleteQueue(queueName);
    }

    @Test
    void persistsOneEventAndPublishesItAfterRetry() {
        CreditApplicationRequest request = new CreditApplicationRequest(
                reference, customerId, new BigDecimal("100.00"), 12);
        assertEquals(CreditApplicationStatus.APPROVED,
                service.process(request).block(Duration.ofSeconds(10)).status());
        service.process(request).block(Duration.ofSeconds(10));
        assertEquals(1L, database.sql("""
                        SELECT count(*) AS total FROM credit_approval_outbox
                        WHERE application_reference = :reference
                        """)
                .bind("reference", reference).map(row -> row.get("total", Long.class))
                .one().block(Duration.ofSeconds(10)));

        RabbitApprovalPublisher unavailable = mock(RabbitApprovalPublisher.class);
        when(unavailable.publish(any())).thenReturn(Mono.error(new IllegalStateException("broker unavailable")));
        new ApprovalOutboxDispatcher(outbox, unavailable, transactionManager)
                .dispatchPending().block(Duration.ofSeconds(10));
        assertFalse(isPublished());

        new ApprovalOutboxDispatcher(outbox, publisher, transactionManager)
                .dispatchPending().block(Duration.ofSeconds(20));
        assertTrue(isPublished());
        Message message = rabbit.receive(queueName, 5000);
        assertNotNull(message);
        var event = mapper.readTree(message.getBody());
        assertEquals(reference, event.path("applicationReference").asText());
        assertEquals("CreditApplicationApproved", event.path("eventType").asText());
        assertEquals(1, event.path("schemaVersion").asInt());
        assertEquals(event.path("eventId").asText(), message.getMessageProperties().getMessageId());
        assertDoesNotThrow(() -> UUID.fromString(event.path("eventId").asText()));

        new ApprovalOutboxDispatcher(outbox, publisher, transactionManager)
                .dispatchPending().block(Duration.ofSeconds(10));
        assertNull(rabbit.receive(queueName));
    }

    @Test
    void rejectedApplicationDoesNotCreateOrPublishEvent() {
        assertEquals(CreditApplicationStatus.REJECTED, service.process(new CreditApplicationRequest(
                reference, customerId, BigDecimal.ZERO, 12)).block(Duration.ofSeconds(10)).status());

        assertEquals(0L, database.sql("""
                        SELECT count(*) AS total FROM credit_approval_outbox
                        WHERE application_reference = :reference
                        """)
                .bind("reference", reference).map(row -> row.get("total", Long.class))
                .one().block(Duration.ofSeconds(10)));
        new ApprovalOutboxDispatcher(outbox, publisher, transactionManager)
                .dispatchPending().block(Duration.ofSeconds(10));
        assertNull(rabbit.receive(queueName));
    }

    private boolean isPublished() {
        return Boolean.TRUE.equals(database.sql("""
                        SELECT published_at IS NOT NULL AS published FROM credit_approval_outbox
                        WHERE application_reference = :reference
                        """)
                .bind("reference", reference).map(row -> row.get("published", Boolean.class))
                .one().block(Duration.ofSeconds(10)));
    }
}
