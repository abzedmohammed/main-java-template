package com.abzed.template.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true")
public class RabbitMqConfig {

    public static final String EXCHANGE = "main-template.exchange";
    public static final String AUTH_EVENTS_QUEUE = "main-template.auth.events";
    public static final String ROUTING_KEY = "auth.event";

    @Bean
    public DirectExchange appExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue authEventsQueue() {
        return QueueBuilder.durable(AUTH_EVENTS_QUEUE).build();
    }

    @Bean
    public Binding authEventsBinding(Queue authEventsQueue, DirectExchange appExchange) {
        return BindingBuilder.bind(authEventsQueue).to(appExchange).with(ROUTING_KEY);
    }
}
