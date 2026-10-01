package com.bancoias.creditapplications.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "bancoias.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitMessagingConfiguration {

    public static final String EXCHANGE = "bancoias.credit.events";
    public static final String ROUTING_KEY = "credit.application.approved";
    public static final String QUEUE = "bancoias.credit.approved";

    @Bean
    public TopicExchange creditEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue creditApprovedQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding creditApprovedBinding(Queue creditApprovedQueue, TopicExchange creditEventsExchange) {
        return BindingBuilder.bind(creditApprovedQueue).to(creditEventsExchange).with(ROUTING_KEY);
    }
}
