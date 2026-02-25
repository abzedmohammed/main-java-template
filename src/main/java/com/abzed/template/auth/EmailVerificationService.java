package com.abzed.template.auth;

import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Value("${app.frontend.verify-email-url:http://localhost:5173/verify-email}")
    private String verifyEmailUrl;

    @Transactional
    public void createAndSendVerificationToken(User user) {
        emailVerificationTokenRepository.deleteByUserId(user.getId());

        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiryDate(Instant.now().plus(24, ChronoUnit.HOURS));
        token.setUsed(false);
        emailVerificationTokenRepository.save(token);

        String link = verifyEmailUrl + "?token=" + token.getToken();
        String body = "Hello " + user.getFullName() + ",\n\n" +
                "Verify your email by visiting:\n" + link + "\n\n" +
                "This verification link expires in 24 hours.";

        emailService.send(user.getEmail(), "Verify your email", body);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Email verification requested",
                "Verification email sent", user.getEmail(), "SUCCESS");
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (token.isUsed() || token.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification token expired or already used");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setUsed(true);
        emailVerificationTokenRepository.save(token);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Email verified",
                "User email marked as verified", user.getEmail(), "SUCCESS");
    }
}
