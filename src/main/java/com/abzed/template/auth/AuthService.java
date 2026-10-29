package com.abzed.template.auth;

import com.abzed.template.common.MaskingUtil;
import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.common.exception.BadRequestException;
import com.abzed.template.common.exception.ConflictException;
import com.abzed.template.common.exception.ForbiddenException;
import com.abzed.template.common.exception.TooManyRequestsException;
import com.abzed.template.common.exception.UnauthorizedException;
import com.abzed.template.config.AuthProperties;
import com.abzed.template.template.MessagingService;
import com.abzed.template.user.AuthProvider;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserRole;
import com.abzed.template.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * OTP-based, multi-step auth. Sign-up / sign-in / reset start a flow and return
 * {@code (reqId, maskedEmail, otpTimer)}; the client then verifies a 4-digit OTP via
 * {@code sign_in_otp}. Login/registration complete the session (access token);
 * the reset flow instead authorizes a one-off {@code update_password}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long RESET_AUTHORIZATION_MINUTES = 15;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final SystemLogService systemLogService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final MessagingService messagingService;
    private final AuthProperties authProperties;

    @Transactional
    public AuthInitResponse signUp(SignUpRequest request) {
        User user = userRepository.findByUsrEmail(request.email().toLowerCase()).orElse(null);

        // Only a DRAFT (never-verified) account may be re-registered; anything else is a duplicate.
        if (user != null && user.getUsrStatus() != UserStatus.DRAFT) {
            throw new ConflictException("Email already in use");
        }

        // A number held by any non-DRAFT account is taken; abandoned drafts don't reserve it.
        if (userRepository.existsByUsrMobileNumberAndUsrStatusNot(request.phone(), UserStatus.DRAFT)) {
            throw new ConflictException("Mobile number already in use");
        }

        boolean resumingDraft = user != null;

        if (user == null) {
            user = new User();
            user.setUsrEmail(request.email().toLowerCase());
            user.setUsrRole(UserRole.USER);
            user.setUsrProvider(AuthProvider.LOCAL);
            user.setUsrStatus(UserStatus.DRAFT);
            user.setUsrEmailVerified(false);
        }

        // New or DRAFT re-registration: (re)apply the submitted details.
        user.setUsrFullName(request.fullName());
        user.setUsrMobileNumber(request.phone());
        user.setUsrPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);

        otpService.issue(saved, OtpPurpose.REGISTRATION);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "User registration",
                resumingDraft
                        ? "Draft account re-registered; a new verification code was sent"
                        : "A new local user account was created and a verification code was sent",
                saved.getUsrEmail(), "SUCCESS");

        return initResponse(saved);
    }

    public AuthInitResponse signIn(SignInRequest request) {
        String email = request.email().toLowerCase();
        if (loginAttemptService.isBlocked(email)) {
            throw new TooManyRequestsException("Account temporarily locked due to too many failed login attempts");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.secret()));
        } catch (AuthenticationException ex) {
            loginAttemptService.onFailure(email);
            throw new UnauthorizedException("Invalid email or password");
        }

        loginAttemptService.onSuccess(email);
        // Checked only after the password is verified so account status isn't revealed to guessers.
        assertAccountUsable(email);
        User user = userRepository.findByUsrEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        otpService.issue(user, OtpPurpose.LOGIN);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Login verification requested",
                "Credentials accepted; a login verification code was sent", user.getUsrEmail(), "SUCCESS");

        return initResponse(user);
    }

    /**
     * Verify an OTP. Returns a completed session for the login/registration flows;
     * returns {@code null} for the password-reset flow (which only authorizes the
     * subsequent {@code update_password}).
     */
    @Transactional
    public AuthSessionResponse verifyOtp(OtpVerifyRequest request) {
        UUID usrId = parseReqId(request.reqId());
        OtpToken token = otpService.verify(usrId, request.otp());
        User user = token.getOtpUser();

        if (token.getOtpPurpose() == OtpPurpose.PASSWORD_RESET) {
            authorizePasswordReset(user);
            systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password reset verified",
                    "Reset OTP verified; password update authorized", user.getUsrEmail(), "SUCCESS");
            return null;
        }

        boolean justActivated = user.getUsrStatus() == UserStatus.DRAFT;
        if (!user.isUsrEmailVerified() || justActivated) {
            user.setUsrEmailVerified(true);
            if (justActivated) {
                user.setUsrStatus(UserStatus.ACTIVE);
            }
            userRepository.save(user);
        }

        // Registration just completed: welcome the new user (best-effort; never fails the session).
        if (justActivated) {
            sendWelcomeEmail(user);
        }

        String accessToken = jwtService.generateAccessToken(user);
        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Session started",
                "OTP verified and session established", user.getUsrEmail(), "SUCCESS");

        return new AuthSessionResponse(accessToken, AuthUser.from(user));
    }

    public void resendOtp(ResendOtpRequest request) {
        otpService.resend(parseReqId(request.reqId()));
    }

    @Transactional
    public AuthInitResponse initiatePasswordReset(ResetPasswordInitRequest request) {
        User user = userRepository.findByUsrEmail(request.email().toLowerCase()).orElse(null);
        if (user != null) {
            assertAccountUsable(user.getUsrEmail());
            otpService.issue(user, OtpPurpose.PASSWORD_RESET);
            systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password reset requested",
                    "Password reset code sent via email", user.getUsrEmail(), "SUCCESS");
            return initResponse(user);
        }
        // No account enumeration: return a believable shape; OTP verification will simply fail.
        return new AuthInitResponse(UUID.randomUUID().toString(), MaskingUtil.maskEmail(request.email()), otpTimer());
    }

    @Transactional
    public void updatePassword(UpdatePasswordRequest request) {
        UUID usrId = parseReqId(request.reqId());
        PasswordResetToken authorization = passwordResetTokenRepository
                .findTopByPrtUser_UsrIdAndPrtUsedFalseOrderByPrtExpiryDateDesc(usrId)
                .filter(prt -> prt.getPrtExpiryDate().isAfter(Instant.now()))
                .orElseThrow(() -> new UnauthorizedException("Password reset not authorized or has expired"));

        User user = authorization.getPrtUser();
        user.setUsrPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);

        authorization.setPrtUsed(true);
        passwordResetTokenRepository.save(authorization);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password reset completed",
                "Password has been reset after OTP verification", user.getUsrEmail(), "SUCCESS");
    }

    public RefreshSessionResponse refreshSession(User user) {
        return new RefreshSessionResponse(jwtService.generateAccessToken(user));
    }

    /** Deactivated accounts are closed for good; any other non-ACTIVE account (draft/inactive) can't proceed yet. */
    private void assertAccountUsable(String email) {
        if (loginAttemptService.isEmailDeactivated(email)) {
            throw new ForbiddenException("This account has been deactivated. Please contact support.");
        }
        if (!loginAttemptService.isEmailActive(email)) {
            throw new UnauthorizedException("Please verify account before continuing");
        }
    }

    private void authorizePasswordReset(User user) {
        passwordResetTokenRepository.deleteByPrtUser_UsrId(user.getUsrId());
        PasswordResetToken authorization = new PasswordResetToken();
        authorization.setPrtUser(user);
        authorization.setPrtToken(UUID.randomUUID().toString().replace("-", ""));
        authorization.setPrtExpiryDate(Instant.now().plus(RESET_AUTHORIZATION_MINUTES, ChronoUnit.MINUTES));
        authorization.setPrtUsed(false);
        passwordResetTokenRepository.save(authorization);
    }

    private void sendWelcomeEmail(User user) {
        try {
            messagingService.sendTemplatedEmail(user.getUsrEmail(), "WELCOME", Map.of(
                    "featureTitle", "You're all set",
                    "featureBody", "Sign in any time to get started."
            ));
            systemLogService.log(SystemLogLevel.INFO, "NOTIFICATION", "Welcome email sent",
                    "Welcome email sent after successful registration", user.getUsrEmail(), "SUCCESS");
        } catch (Exception ex) {
            // Best-effort: a welcome email must never block or roll back a completed registration.
            log.warn("Failed to send welcome email to {}: {}", user.getUsrEmail(), ex.getMessage());
        }
    }

    private AuthInitResponse initResponse(User user) {
        return new AuthInitResponse(user.getUsrId().toString(), MaskingUtil.maskEmail(user.getUsrEmail()), otpTimer());
    }

    /** OTP lifetime in minutes; the client multiplies by 60 for its countdown. */
    private Long otpTimer() {
        return authProperties.getOtp().getTtlMinutes();
    }

    private UUID parseReqId(String reqId) {
        try {
            return UUID.fromString(reqId);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid session reference");
        }
    }
}
