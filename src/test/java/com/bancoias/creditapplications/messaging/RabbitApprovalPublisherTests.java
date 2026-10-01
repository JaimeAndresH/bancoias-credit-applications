package com.bancoias.creditapplications.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.bancoias.creditapplications.config.RabbitMessagingConfiguration.EXCHANGE;
import static com.bancoias.creditapplications.config.RabbitMessagingConfiguration.ROUTING_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RabbitApprovalPublisherTests {

    @Test
    void sendsPersistentJsonAndWaitsForConfirmation() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        UUID eventId = UUID.randomUUID();
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            assertEquals(eventId.toString(), message.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals("application/json", message.getMessageProperties().getContentType());
            assertEquals("{\"eventId\":\"example\"}", new String(message.getBody(), StandardCharsets.UTF_8));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(EXCHANGE), eq(ROUTING_KEY), any(Message.class), any(CorrelationData.class));

        StepVerifier.create(new RabbitApprovalPublisher(rabbit).publish(
                new ApprovalEventOutbox.PendingEvent(eventId, "{\"eventId\":\"example\"}")))
                .verifyComplete();
    }

    @Test
    void failsWhenBrokerRejectsMessage() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "rejected"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        StepVerifier.create(new RabbitApprovalPublisher(rabbit).publish(
                new ApprovalEventOutbox.PendingEvent(UUID.randomUUID(), "{}")))
                .expectError(IllegalStateException.class).verify();
    }

    @Test
    void failsWhenConfirmedMessageHasNoDestinationQueue() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2),
                    312, "NO_ROUTE", EXCHANGE, ROUTING_KEY));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        StepVerifier.create(new RabbitApprovalPublisher(rabbit).publish(
                new ApprovalEventOutbox.PendingEvent(UUID.randomUUID(), "{}")))
                .expectError(IllegalStateException.class).verify();
    }
}
