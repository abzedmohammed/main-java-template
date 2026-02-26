package com.abzed.template.common;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class SystemLogSpecifications {

    public static Specification<SystemLog> hasLevel(SystemLogLevel level) {
        return (root, query, cb) -> level == null ? cb.conjunction() : cb.equal(root.get("level"), level);
    }

    public static Specification<SystemLog> hasCategory(String category) {
        return (root, query, cb) -> category == null || category.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("category")), category.toLowerCase());
    }

    public static Specification<SystemLog> fromDate(Instant from) {
        return (root, query, cb) -> from == null ? cb.conjunction() : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<SystemLog> toDate(Instant to) {
        return (root, query, cb) -> to == null ? cb.conjunction() : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
