package com.abzed.template.messaging;

import com.abzed.template.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true")
public class AuthEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String event, String email, String status, String details) {
        try {
            AuthEventMessage message = new AuthEventMessage(event, email, status, details, Instant.now());
            rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE, RabbitMqConfig.ROUTING_KEY, message);
        } catch (Exception ex) {
            log.warn("Failed to publish auth event to RabbitMQ: {}", ex.getMessage());
        }
    }
}
