package com.abzed.template.template;

import com.abzed.template.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a reusable {@link MessageTemplate} by code, interpolates it with runtime
 * variables, wraps the result in the referenced {@link EmailTemplate} design, and sends it.
 * Also exposes plain-text rendering for the SMS / in-app channels.
 */
@Service
@RequiredArgsConstructor
public class MessagingService {

    private final MessageTemplateRepository messageTemplateRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final TemplateRenderer renderer;
    private final EmailService emailService;

    @Value("${app.name:Main Java Template}")
    private String appName;

    @Value("${app.frontend.login-url:http://localhost:5173/login}")
    private String loginUrl;

    @Value("${app.support-email:support@example.com}")
    private String supportEmail;

    public void sendTemplatedEmail(String to, String templateCode, Map<String, Object> variables) {
        MessageTemplate template = messageTemplateRepository.findByMtpCodeAndMtpActiveTrue(templateCode)
                .orElseThrow(() -> new NotFoundException("Message template not found: " + templateCode));

        Map<String, Object> vars = baseVariables();
        if (variables != null) {
            vars.putAll(variables);
        }

        String subject = renderer.render(template.getMtpSubject(), vars);
        vars.put("subject", subject);
        vars.put("title", renderer.render(template.getMtpTitle(), vars));
        vars.put("body", renderer.render(template.getMtpBody(), vars));

        String html;
        if (template.getMtpLayoutCode() != null && !template.getMtpLayoutCode().isBlank()) {
            EmailTemplate layout = emailTemplateRepository.findByEmlCodeAndEmlActiveTrue(template.getMtpLayoutCode())
                    .orElseThrow(() -> new NotFoundException("Email layout not found: " + template.getMtpLayoutCode()));
            html = renderer.render(layout.getEmlHtml(), vars);
        } else {
            html = String.valueOf(vars.get("body"));
        }

        emailService.sendHtml(to, subject, html);
    }

    /** Interpolated plain text for non-email channels (SMS / in-app). */
    public String renderText(String templateCode, Map<String, Object> variables) {
        MessageTemplate template = messageTemplateRepository.findByMtpCodeAndMtpActiveTrue(templateCode)
                .orElseThrow(() -> new NotFoundException("Message template not found: " + templateCode));
        Map<String, Object> vars = baseVariables();
        if (variables != null) {
            vars.putAll(variables);
        }
        return renderer.render(template.getMtpBody(), vars);
    }

    private Map<String, Object> baseVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("appName", appName);
        vars.put("year", String.valueOf(Year.now().getValue()));
        vars.put("actionUrl", loginUrl);
        vars.put("actionLabel", "Go to Login Page");
        vars.put("ctaUrl", loginUrl);
        vars.put("ctaLabel", "Open " + appName);
        vars.put("supportEmail", supportEmail);
        vars.put("featureTitle", "");
        vars.put("featureBody", "");
        return vars;
    }
}
