package com.abzed.template.common;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogRepository systemLogRepository;
    private final SystemLogService systemLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> getLogs() {
        List<Map<String, Object>> logs = systemLogRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(log -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", log.getId());
                    item.put("when", log.getCreatedAt());
                    item.put("level", log.getLevel());
                    item.put("category", log.getCategory());
                    item.put("title", log.getTitle());
                    item.put("details", log.getDetails());
                    item.put("actor", log.getActor() == null ? "System" : log.getActor());
                    item.put("status", log.getStatus());
                    item.put("humanMessage", systemLogService.humanMessage(log));
                    return item;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success("System logs", logs));
    }
}
