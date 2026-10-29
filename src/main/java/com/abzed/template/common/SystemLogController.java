package com.abzed.template.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getLogs(
            @RequestParam(required = false) SystemLogLevel level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Specification<SystemLog> spec = Specification
                .where(SystemLogSpecifications.hasLevel(level))
                .and(SystemLogSpecifications.hasCategory(category))
                .and(SystemLogSpecifications.fromDate(from))
                .and(SystemLogSpecifications.toDate(to));

        var result = systemLogRepository.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Map<String, Object>> logs = result.getContent().stream()
                .map(log -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("slgId", log.getSlgId());
                    item.put("createdAt", log.getCreatedAt());
                    item.put("slgLevel", log.getSlgLevel());
                    item.put("slgCategory", log.getSlgCategory());
                    item.put("slgTitle", log.getSlgTitle());
                    item.put("slgDetails", log.getSlgDetails());
                    item.put("slgActor", log.getSlgActor() == null ? "System" : log.getSlgActor());
                    item.put("slgStatus", log.getSlgStatus());
                    item.put("humanMessage", systemLogService.humanMessage(log));
                    return item;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.page("System logs", logs, result.getTotalElements()));
    }
}
