package com.abzed.template.auth;

import com.abzed.template.common.exception.BadRequestException;
import com.abzed.template.common.exception.TooManyRequestsException;
import com.abzed.template.config.AuthProperties;
import com.abzed.template.template.MessagingService;
import com.abzed.template.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Issues, re-sends, and verifies short-lived 4-digit OTPs for the pre-auth flows.
 * One active OTP per user: issuing a new code clears prior ones. Codes are stored
 * only as a keyed hash, expire, are single-use, and verification attempts are capped.
 * Delivery uses the DB-backed templated email for the flow's purpose ({@code OTP_*}).
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final MessagingService messagingService;
    private final OtpHasher otpHasher;
    private final AuthProperties authProperties;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public OtpToken issue(User user, OtpPurpose purpose) {
        otpTokenRepository.deleteByOtpUser_UsrId(user.getUsrId());

        String code = String.format("%04d", random.nextInt(10_000));
        long ttlMinutes = authProperties.getOtp().getTtlMinutes();

        OtpToken token = new OtpToken();
        token.setOtpUser(user);
        token.setOtpCodeHash(otpHasher.hash(user.getUsrId(), purpose, code));
        token.setOtpPurpose(purpose);
        token.setOtpExpiryDate(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        token.setOtpConsumed(false);
        token.setOtpAttempts(0);
        otpTokenRepository.save(token);

        messagingService.sendTemplatedEmail(user.getUsrEmail(), templateCodeFor(purpose), Map.of(
                "otpCode", code,
                "expiryText", "Expires in " + ttlMinutes + " minutes"
        ));

        return token;
    }

    @Transactional
    public void resend(UUID usrId) {
        OtpToken last = otpTokenRepository.findTopByOtpUser_UsrIdOrderByCreatedAtDesc(usrId)
                .orElseThrow(() -> new BadRequestException(
                        "Your verification session has expired. Please start the sign-in or sign-up again."));
        issue(last.getOtpUser(), last.getOtpPurpose());
    }

    @Transactional
    public OtpToken verify(UUID usrId, String code) {
        OtpToken token = otpTokenRepository.findTopByOtpUser_UsrIdAndOtpConsumedFalseOrderByCreatedAtDesc(usrId)
                .orElseThrow(() -> new BadRequestException("Invalid or expired code"));

        if (token.getOtpExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Invalid or expired code");
        }
        if (token.getOtpAttempts() >= authProperties.getOtp().getMaxAttempts()) {
            throw new TooManyRequestsException("Too many incorrect attempts. Request a new code.");
        }
        if (!otpHasher.matches(usrId, token.getOtpPurpose(), code, token.getOtpCodeHash())) {
            token.setOtpAttempts(token.getOtpAttempts() + 1);
            otpTokenRepository.save(token);
            throw new BadRequestException("Invalid or expired code");
        }

        token.setOtpConsumed(true);
        otpTokenRepository.save(token);
        return token;
    }

    private String templateCodeFor(OtpPurpose purpose) {
        return switch (purpose) {
            case LOGIN -> "OTP_LOGIN";
            case REGISTRATION -> "OTP_REGISTRATION";
            case PASSWORD_RESET -> "OTP_PASSWORD_RESET";
        };
    }
}
