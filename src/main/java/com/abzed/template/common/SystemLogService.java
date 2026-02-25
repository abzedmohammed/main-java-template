package com.abzed.template.common;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogRepository systemLogRepository;

    public void log(SystemLogLevel level, String category, String title, String details, String actor, String status) {
        SystemLog log = new SystemLog();
        log.setLevel(level);
        log.setCategory(category);
        log.setTitle(title);
        log.setDetails(details);
        log.setActor(actor);
        log.setStatus(status);
        systemLogRepository.save(log);
    }

    public String humanMessage(SystemLog log) {
        String actor = log.getActor() == null || log.getActor().isBlank() ? "System" : log.getActor();
        return "[" + log.getLevel() + "] " + actor + " - " + log.getTitle() + " (" + log.getStatus() + ")";
    }
}
