package com.abzed.template.auth;

import com.abzed.template.common.exception.BadRequestException;
import com.abzed.template.common.exception.TooManyRequestsException;
import com.abzed.template.config.AuthProperties;
import com.abzed.template.template.MessagingService;
import com.abzed.template.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    private static final UUID USR = UUID.randomUUID();

    @Mock
    private OtpTokenRepository otpTokenRepository;

    @Mock
    private MessagingService messagingService;

    private final OtpHasher otpHasher = new OtpHasher("unit-test-otp-hmac-secret-0123456789ab");

    private OtpService otpService;

    @BeforeEach
    void setup() {
        AuthProperties props = new AuthProperties();
        props.getOtp().setTtlMinutes(5);
        props.getOtp().setMaxAttempts(5);
        otpService = new OtpService(otpTokenRepository, messagingService, otpHasher, props);
    }

    @Test
    @SuppressWarnings("unchecked")
    void issueEmailsAFourDigitCodeAndStoresOnlyItsHash() {
        User user = user();
        when(otpTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OtpToken token = otpService.issue(user, OtpPurpose.LOGIN);

        verify(otpTokenRepository).deleteByOtpUser_UsrId(USR);
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(messagingService).sendTemplatedEmail(eq(user.getUsrEmail()), eq("OTP_LOGIN"), vars.capture());
        String emailedCode = (String) vars.getValue().get("otpCode");

        assertTrue(emailedCode.matches("\\d{4}"), "code should be 4 digits");
        assertFalse(token.isOtpConsumed());
        assertNotEquals(emailedCode, token.getOtpCodeHash());
        assertTrue(token.getOtpCodeHash().matches("[0-9a-f]{64}"), "stored value should be a hex HMAC-SHA256");
        assertEquals(token.getOtpCodeHash(), otpHasher.hash(USR, OtpPurpose.LOGIN, emailedCode));
    }

    @Test
    void hashIsBoundToTheUserAndPurposeSoItCannotBeReplayed() {
        String hash = otpHasher.hash(USR, OtpPurpose.LOGIN, "1234");

        assertTrue(otpHasher.matches(USR, OtpPurpose.LOGIN, "1234", hash));
        assertFalse(otpHasher.matches(USR, OtpPurpose.LOGIN, "1235", hash));
        assertFalse(otpHasher.matches(UUID.randomUUID(), OtpPurpose.LOGIN, "1234", hash));
        assertFalse(otpHasher.matches(USR, OtpPurpose.PASSWORD_RESET, "1234", hash));
    }

    @Test
    void hasherRefusesAMissingOrShortSecret() {
        assertThrows(IllegalStateException.class, () -> new OtpHasher(""));
        assertThrows(IllegalStateException.class, () -> new OtpHasher("too-short"));
    }

    @Test
    void verifyConsumesAMatchingCode() {
        OtpToken token = otp("1234", Instant.now().plus(2, ChronoUnit.MINUTES), 0);
        when(otpTokenRepository.findTopByOtpUser_UsrIdAndOtpConsumedFalseOrderByCreatedAtDesc(USR))
                .thenReturn(Optional.of(token));
        when(otpTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OtpToken verified = otpService.verify(USR, "1234");

        assertTrue(verified.isOtpConsumed());
    }

    @Test
    void verifyRejectsAndCountsAWrongCode() {
        OtpToken token = otp("1234", Instant.now().plus(2, ChronoUnit.MINUTES), 0);
        when(otpTokenRepository.findTopByOtpUser_UsrIdAndOtpConsumedFalseOrderByCreatedAtDesc(USR))
                .thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class, () -> otpService.verify(USR, "0000"));
        assertEquals(1, token.getOtpAttempts());
        assertFalse(token.isOtpConsumed());
    }

    @Test
    void verifyRejectsAnExpiredCode() {
        OtpToken token = otp("1234", Instant.now().minus(1, ChronoUnit.MINUTES), 0);
        when(otpTokenRepository.findTopByOtpUser_UsrIdAndOtpConsumedFalseOrderByCreatedAtDesc(USR))
                .thenReturn(Optional.of(token));

        assertThrows(BadRequestException.class, () -> otpService.verify(USR, "1234"));
    }

    @Test
    void verifyBlocksAfterTooManyAttempts() {
        OtpToken token = otp("1234", Instant.now().plus(2, ChronoUnit.MINUTES), 5);
        when(otpTokenRepository.findTopByOtpUser_UsrIdAndOtpConsumedFalseOrderByCreatedAtDesc(USR))
                .thenReturn(Optional.of(token));

        assertThrows(TooManyRequestsException.class, () -> otpService.verify(USR, "1234"));
    }

    private User user() {
        User user = new User();
        user.setUsrId(USR);
        user.setUsrEmail("otp.user@example.com");
        user.setUsrFullName("Otp User");
        return user;
    }

    private OtpToken otp(String code, Instant expiry, int attempts) {
        OtpToken token = new OtpToken();
        token.setOtpUser(user());
        token.setOtpCodeHash(otpHasher.hash(USR, OtpPurpose.LOGIN, code));
        token.setOtpPurpose(OtpPurpose.LOGIN);
        token.setOtpExpiryDate(expiry);
        token.setOtpAttempts(attempts);
        token.setOtpConsumed(false);
        return token;
    }
}
