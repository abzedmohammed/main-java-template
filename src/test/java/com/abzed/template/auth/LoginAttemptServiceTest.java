package com.abzed.template.auth;

import com.abzed.template.auth.ratelimit.LoginRateLimit;
import com.abzed.template.auth.ratelimit.LoginRateLimitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private LoginRateLimitRepository repository;

    private LoginAttemptService service() {
        return new LoginAttemptService(repository);
    }

    @Test
    void unknownEmailIsNotBlocked() {
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        assertFalse(service().isBlocked("new@example.com"));
    }

    @Test
    void emailWithActiveLockIsBlocked() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setEmail("locked@example.com");
        entry.setBlockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findByEmail("locked@example.com")).thenReturn(Optional.of(entry));

        assertTrue(service().isBlocked("locked@example.com"));
    }

    @Test
    void expiredLockIsNotBlocked() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setEmail("expired@example.com");
        entry.setBlockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findByEmail("expired@example.com")).thenReturn(Optional.of(entry));

        assertFalse(service().isBlocked("expired@example.com"));
    }

    @Test
    void fifthFailureSetsLock() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setEmail("user@example.com");
        entry.setAttempts(4); // next failure is the 5th
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(entry));

        service().onFailure("user@example.com");

        ArgumentCaptor<LoginRateLimit> saved = ArgumentCaptor.forClass(LoginRateLimit.class);
        verify(repository).save(saved.capture());
        assertNotNull(saved.getValue().getBlockedUntil());
        assertTrue(saved.getValue().getBlockedUntil().isAfter(Instant.now()));
    }

    @Test
    void earlyFailureDoesNotLock() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setEmail("user@example.com");
        entry.setAttempts(1);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(entry));

        service().onFailure("user@example.com");

        ArgumentCaptor<LoginRateLimit> saved = ArgumentCaptor.forClass(LoginRateLimit.class);
        verify(repository).save(saved.capture());
        assertNull(saved.getValue().getBlockedUntil());
    }

    @Test
    void successClearsAnyExistingEntry() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setEmail("user@example.com");
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(entry));

        service().onSuccess("user@example.com");

        verify(repository).delete(entry);
    }

    @Test
    void successWithNoEntryDoesNothing() {
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        service().onSuccess("user@example.com");

        verify(repository, org.mockito.Mockito.never()).delete(any());
    }
}
