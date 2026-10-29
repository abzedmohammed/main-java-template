package com.abzed.template.auth;

import com.abzed.template.auth.ratelimit.LoginRateLimit;
import com.abzed.template.auth.ratelimit.LoginRateLimitRepository;
import com.abzed.template.config.AuthProperties;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserStatus;
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

    @Mock
    private UserRepository userRepository;

    private LoginAttemptService service() {
        return new LoginAttemptService(repository, userRepository, new AuthProperties());
    }

    private void userWithStatus(String email, UserStatus status) {
        User user = new User();
        user.setUsrEmail(email);
        user.setUsrStatus(status);
        when(userRepository.findByUsrEmail(email)).thenReturn(Optional.of(user));
    }

    @Test
    void onlyAnActiveAccountIsReportedActive() {
        userWithStatus("active@example.com", UserStatus.ACTIVE);
        assertTrue(service().isEmailActive("Active@Example.com"));
    }

    @Test
    void draftAndInactiveAccountsAreNotActive() {
        userWithStatus("draft@example.com", UserStatus.DRAFT);
        userWithStatus("inactive@example.com", UserStatus.INACTIVE);

        assertFalse(service().isEmailActive("draft@example.com"));
        assertFalse(service().isEmailActive("inactive@example.com"));
    }

    @Test
    void onlyADeactivatedAccountIsReportedDeactivated() {
        userWithStatus("gone@example.com", UserStatus.DEACTIVATED);
        userWithStatus("fine@example.com", UserStatus.ACTIVE);

        assertTrue(service().isEmailDeactivated("gone@example.com"));
        assertFalse(service().isEmailDeactivated("fine@example.com"));
    }

    @Test
    void unknownEmailIsNeitherActiveNorDeactivated() {
        when(userRepository.findByUsrEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertFalse(service().isEmailActive("nobody@example.com"));
        assertFalse(service().isEmailDeactivated("nobody@example.com"));
    }

    @Test
    void unknownEmailIsNotBlocked() {
        when(repository.findByLrlEmail("new@example.com")).thenReturn(Optional.empty());

        assertFalse(service().isBlocked("new@example.com"));
    }

    @Test
    void emailWithActiveLockIsBlocked() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setLrlEmail("locked@example.com");
        entry.setLrlBlockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(repository.findByLrlEmail("locked@example.com")).thenReturn(Optional.of(entry));

        assertTrue(service().isBlocked("locked@example.com"));
    }

    @Test
    void expiredLockIsNotBlocked() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setLrlEmail("expired@example.com");
        entry.setLrlBlockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findByLrlEmail("expired@example.com")).thenReturn(Optional.of(entry));

        assertFalse(service().isBlocked("expired@example.com"));
    }

    @Test
    void fifthFailureSetsLock() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setLrlEmail("user@example.com");
        entry.setLrlAttempts(4); // next failure is the 5th
        when(repository.findByLrlEmail("user@example.com")).thenReturn(Optional.of(entry));

        service().onFailure("user@example.com");

        ArgumentCaptor<LoginRateLimit> saved = ArgumentCaptor.forClass(LoginRateLimit.class);
        verify(repository).save(saved.capture());
        assertNotNull(saved.getValue().getLrlBlockedUntil());
        assertTrue(saved.getValue().getLrlBlockedUntil().isAfter(Instant.now()));
    }

    @Test
    void earlyFailureDoesNotLock() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setLrlEmail("user@example.com");
        entry.setLrlAttempts(1);
        when(repository.findByLrlEmail("user@example.com")).thenReturn(Optional.of(entry));

        service().onFailure("user@example.com");

        ArgumentCaptor<LoginRateLimit> saved = ArgumentCaptor.forClass(LoginRateLimit.class);
        verify(repository).save(saved.capture());
        assertNull(saved.getValue().getLrlBlockedUntil());
    }

    @Test
    void successClearsAnyExistingEntry() {
        LoginRateLimit entry = new LoginRateLimit();
        entry.setLrlEmail("user@example.com");
        when(repository.findByLrlEmail("user@example.com")).thenReturn(Optional.of(entry));

        service().onSuccess("user@example.com");

        verify(repository).delete(entry);
    }

    @Test
    void successWithNoEntryDoesNothing() {
        when(repository.findByLrlEmail("user@example.com")).thenReturn(Optional.empty());

        service().onSuccess("user@example.com");

        verify(repository, org.mockito.Mockito.never()).delete(any());
    }
}
