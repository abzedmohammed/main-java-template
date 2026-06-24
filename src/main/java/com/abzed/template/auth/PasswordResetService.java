package com.abzed.template.auth;

import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.common.exception.BadRequestException;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SystemLogService systemLogService;

    @Value("${app.frontend.reset-password-url:http://localhost:5173/reset-password}")
    private String resetPasswordUrl;

    @Transactional
    public void createAndSendResetToken(String email) {
        // Do not reveal whether the email exists (prevents account enumeration);
        // the controller always responds with a generic success message.
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email; ignoring");
            return;
        }

        passwordResetTokenRepository.deleteByUserId(user.getId());

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setExpiryDate(Instant.now().plus(30, ChronoUnit.MINUTES));
        token.setUsed(false);
        passwordResetTokenRepository.save(token);

        String link = resetPasswordUrl + "?token=" + token.getToken();
        String body = "Hello " + user.getFullName() + ",\n\n" +
                "Use this link to reset your password:\n" + link + "\n\n" +
                "This link expires in 30 minutes.";

        emailService.send(user.getEmail(), "Password Reset Request", body);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password reset requested",
                "Password reset link sent via email", user.getEmail(), "SUCCESS");
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (token.isUsed() || token.getExpiryDate().isBefore(Instant.now())) {
            throw new BadRequestException("Reset token expired or already used");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password reset completed",
                "Password has been reset using reset token", user.getEmail(), "SUCCESS");
    }
}
