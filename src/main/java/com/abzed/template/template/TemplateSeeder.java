package com.abzed.template.template;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Seeds the default email layouts and message templates on startup if absent.
 * Rows live in the DB as the runtime source of truth (editable without a redeploy);
 * these defaults just bootstrap a fresh database.
 */
@Component
@RequiredArgsConstructor
public class TemplateSeeder implements ApplicationRunner {

    private final EmailTemplateRepository emailTemplateRepository;
    private final MessageTemplateRepository messageTemplateRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedLayout("OTP", "OTP Verification", "templates/email/otp.html");
        seedLayout("GENERAL", "General Notification", "templates/email/general.html");

        seedMessage("OTP_LOGIN", "Your {{appName}} login code", "Verify Your Identity",
                "Welcome back. Use the verification code below to complete your login.", "OTP");
        seedMessage("OTP_REGISTRATION", "Verify your {{appName}} account", "Welcome to {{appName}}",
                "Thanks for joining. Use the verification code below to verify your email and activate your account.", "OTP");
        seedMessage("OTP_PASSWORD_RESET", "Your {{appName}} password reset code", "Reset Your Password",
                "Use the verification code below to continue resetting your password. If you didn't request this, you can safely ignore this email.", "OTP");
        seedMessage("WELCOME", "Welcome to {{appName}}", "Your account is ready",
                "Your email is verified and your {{appName}} account is active. Sign in any time to pick up where you left off.", "GENERAL");
    }

    private void seedLayout(String code, String name, String resource) {
        if (emailTemplateRepository.findByEmlCode(code).isPresent()) {
            return;
        }
        EmailTemplate layout = new EmailTemplate();
        layout.setEmlCode(code);
        layout.setEmlName(name);
        layout.setEmlHtml(readResource(resource));
        layout.setEmlActive(true);
        emailTemplateRepository.save(layout);
    }

    private void seedMessage(String code, String subject, String title, String body, String layoutCode) {
        if (messageTemplateRepository.findByMtpCode(code).isPresent()) {
            return;
        }
        MessageTemplate template = new MessageTemplate();
        template.setMtpCode(code);
        template.setMtpChannel(MessageChannel.EMAIL);
        template.setMtpSubject(subject);
        template.setMtpTitle(title);
        template.setMtpBody(body);
        template.setMtpLayoutCode(layoutCode);
        template.setMtpActive(true);
        messageTemplateRepository.save(template);
    }

    private String readResource(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read email template resource: " + path, ex);
        }
    }
}
