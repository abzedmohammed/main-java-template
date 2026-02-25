package com.abzed.template.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public boolean isBlocked(String email) {
        Instant until = lockedUntil.get(email.toLowerCase());
        if (until == null) {
            return false;
        }

        if (until.isBefore(Instant.now())) {
            lockedUntil.remove(email.toLowerCase());
            attempts.remove(email.toLowerCase());
            return false;
        }

        return true;
    }

    public void onSuccess(String email) {
        attempts.remove(email.toLowerCase());
        lockedUntil.remove(email.toLowerCase());
    }

    public void onFailure(String email) {
        String key = email.toLowerCase();
        int next = attempts.getOrDefault(key, 0) + 1;
        attempts.put(key, next);

        if (next >= MAX_ATTEMPTS) {
            lockedUntil.put(key, Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
        }
    }
}
