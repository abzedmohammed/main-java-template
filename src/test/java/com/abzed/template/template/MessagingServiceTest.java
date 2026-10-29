package com.abzed.template.template;

import com.abzed.template.common.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagingServiceTest {

    private final MessageTemplateRepository messageTemplateRepository = mock(MessageTemplateRepository.class);
    private final EmailTemplateRepository emailTemplateRepository = mock(EmailTemplateRepository.class);
    private final EmailService emailService = mock(EmailService.class);

    private MessagingService messagingService;

    @BeforeEach
    void setup() {
        messagingService = new MessagingService(
                messageTemplateRepository, emailTemplateRepository, new TemplateRenderer(), emailService);
        ReflectionTestUtils.setField(messagingService, "appName", "Acme");
        ReflectionTestUtils.setField(messagingService, "loginUrl", "http://localhost:5173/login");
    }

    @Test
    void rendersContentIntoLayoutThenSendsHtml() {
        MessageTemplate content = new MessageTemplate();
        content.setMtpCode("OTP_LOGIN");
        content.setMtpChannel(MessageChannel.EMAIL);
        content.setMtpSubject("Your {{appName}} login code");
        content.setMtpTitle("Verify Your Identity");
        content.setMtpBody("Use the code below.");
        content.setMtpLayoutCode("OTP");
        content.setMtpActive(true);
        when(messageTemplateRepository.findByMtpCodeAndMtpActiveTrue("OTP_LOGIN")).thenReturn(Optional.of(content));

        EmailTemplate layout = new EmailTemplate();
        layout.setEmlCode("OTP");
        layout.setEmlHtml("<h1>{{title}}</h1><b>{{otpCode}}</b><p>{{body}} {{appName}} {{year}}</p>");
        layout.setEmlActive(true);
        when(emailTemplateRepository.findByEmlCodeAndEmlActiveTrue("OTP")).thenReturn(Optional.of(layout));

        messagingService.sendTemplatedEmail("jane@example.com", "OTP_LOGIN",
                Map.of("otpCode", "1234", "expiryText", "Expires in 5 minutes"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendHtml(eq("jane@example.com"), subject.capture(), html.capture());

        assertEquals("Your Acme login code", subject.getValue());
        assertTrue(html.getValue().contains("1234"), "OTP code should be interpolated into the layout");
        assertTrue(html.getValue().contains("Verify Your Identity"), "content title should be in the layout");
        assertTrue(html.getValue().contains("Use the code below."), "content body should be in the layout");
        assertTrue(html.getValue().contains("Acme"), "appName default should resolve");
    }

    @Test
    void missingTemplateThrows() {
        when(messageTemplateRepository.findByMtpCodeAndMtpActiveTrue("NOPE")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> messagingService.sendTemplatedEmail("a@b.com", "NOPE", Map.of()));
    }
}
