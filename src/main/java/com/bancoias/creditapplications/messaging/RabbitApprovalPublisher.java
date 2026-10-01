package com.bancoias.creditapplications.messaging;

import com.bancoias.creditapplications.config.RabbitMessagingConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class RabbitApprovalPublisher {

    private final RabbitTemplate rabbit;

    public RabbitApprovalPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public Mono<Void> publish(ApprovalEventOutbox.PendingEvent event) {
        return Mono.defer(() -> {
            CorrelationData correlation = new CorrelationData(event.eventId().toString());
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding(StandardCharsets.UTF_8.name());
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(event.eventId().toString());
            properties.setType("CreditApplicationApproved");
            Message message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), properties);

            return Mono.fromRunnable(() -> rabbit.send(RabbitMessagingConfiguration.EXCHANGE,
                            RabbitMessagingConfiguration.ROUTING_KEY, message, correlation))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then(Mono.defer(() -> Mono.fromFuture(correlation.getFuture())))
                    .timeout(Duration.ofSeconds(10))
                    .flatMap(confirm -> confirm.ack() && correlation.getReturned() == null
                            ? Mono.<Void>empty()
                            : Mono.error(new IllegalStateException("RabbitMQ no confirmo el enrutamiento")));
        });
    }
}
