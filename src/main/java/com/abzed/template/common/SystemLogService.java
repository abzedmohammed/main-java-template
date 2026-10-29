package com.abzed.template.common;

import com.abzed.template.messaging.AuthEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;
    private final ObjectProvider<AuthEventPublisher> authEventPublisher;

    public void log(SystemLogLevel level, String category, String title, String details, String actor, String status) {
        SystemLog log = new SystemLog();
        log.setSlgLevel(level);
        log.setSlgCategory(category);
        log.setSlgTitle(title);
        log.setSlgDetails(details);
        log.setSlgActor(actor);
        log.setSlgStatus(status);
        systemLogRepository.save(log);

        if ("AUTH".equalsIgnoreCase(category)) {
            AuthEventPublisher publisher = authEventPublisher.getIfAvailable();
            if (publisher != null) {
                publisher.publish(title, actor, status, details);
            }
        }
    }

    public String humanMessage(SystemLog log) {
        String actor = log.getSlgActor() == null || log.getSlgActor().isBlank() ? "System" : log.getSlgActor();
        return "[" + log.getSlgLevel() + "] " + actor + " - " + log.getSlgTitle() + " (" + log.getSlgStatus() + ")";
    }
}
