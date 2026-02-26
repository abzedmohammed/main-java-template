package com.abzed.template.messaging;

import com.abzed.template.config.RabbitMqConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.rabbitmq", name = "enabled", havingValue = "true")
public class AuthEventListener {

    @RabbitListener(queues = RabbitMqConfig.AUTH_EVENTS_QUEUE)
    public void handle(AuthEventMessage message) {
        log.info("[RABBIT-AUTH-EVENT] {} {} {}", message.event(), message.email(), message.status());
    }
}
