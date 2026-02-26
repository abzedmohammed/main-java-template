package com.abzed.template.auth.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LoginRateLimitRepository extends JpaRepository<LoginRateLimit, UUID> {
    Optional<LoginRateLimit> findByEmail(String email);
}
