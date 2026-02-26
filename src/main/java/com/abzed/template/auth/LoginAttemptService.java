package com.abzed.template.auth;

import com.abzed.template.auth.ratelimit.LoginRateLimit;
import com.abzed.template.auth.ratelimit.LoginRateLimitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final LoginRateLimitRepository loginRateLimitRepository;

    public boolean isBlocked(String email) {
        String key = email.toLowerCase();
        return loginRateLimitRepository.findByEmail(key)
                .map(entry -> entry.getBlockedUntil() != null && entry.getBlockedUntil().isAfter(Instant.now()))
                .orElse(false);
    }

    public void onSuccess(String email) {
        String key = email.toLowerCase();
        loginRateLimitRepository.findByEmail(key).ifPresent(loginRateLimitRepository::delete);
    }

    public void onFailure(String email) {
        String key = email.toLowerCase();
        LoginRateLimit entry = loginRateLimitRepository.findByEmail(key).orElseGet(LoginRateLimit::new);

        int next = entry.getAttempts() + 1;
        entry.setEmail(key);
        entry.setAttempts(next);
        entry.setUpdatedAt(Instant.now());

        if (next >= MAX_ATTEMPTS) {
            entry.setBlockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
        }

        loginRateLimitRepository.save(entry);
    }
}
