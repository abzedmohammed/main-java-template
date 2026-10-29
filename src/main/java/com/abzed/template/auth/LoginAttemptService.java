package com.abzed.template.auth;

import com.abzed.template.auth.ratelimit.LoginRateLimit;
import com.abzed.template.auth.ratelimit.LoginRateLimitRepository;
import com.abzed.template.config.AuthProperties;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final LoginRateLimitRepository loginRateLimitRepository;
    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public boolean isBlocked(String email) {
        String key = email.toLowerCase();
        return loginRateLimitRepository.findByLrlEmail(key)
                .map(entry -> entry.getLrlBlockedUntil() != null && entry.getLrlBlockedUntil().isAfter(Instant.now()))
                .orElse(false);
    }

    public boolean isEmailActive(String email) {
        return hasStatus(email, UserStatus.ACTIVE);
    }

    public boolean isEmailDeactivated(String email) {
        return hasStatus(email, UserStatus.DEACTIVATED);
    }

    public void onSuccess(String email) {
        String key = email.toLowerCase();
        loginRateLimitRepository.findByLrlEmail(key).ifPresent(loginRateLimitRepository::delete);
    }

    public void onFailure(String email) {
        String key = email.toLowerCase();
        LoginRateLimit entry = loginRateLimitRepository.findByLrlEmail(key).orElseGet(LoginRateLimit::new);

        int next = entry.getLrlAttempts() + 1;
        entry.setLrlEmail(key);
        entry.setLrlAttempts(next);
        entry.setLrlUpdatedAt(Instant.now());

        if (next >= authProperties.getLogin().getMaxAttempts()) {
            entry.setLrlBlockedUntil(Instant.now().plus(authProperties.getLogin().getLockMinutes(), ChronoUnit.MINUTES));
        }

        loginRateLimitRepository.save(entry);
    }

    private boolean hasStatus(String email, UserStatus status) {
        User user = userRepository.findByUsrEmail(email.toLowerCase()).orElse(null);
        return user != null && user.getUsrStatus() == status;
    }
}
