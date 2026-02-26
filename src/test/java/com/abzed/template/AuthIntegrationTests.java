package com.abzed.template;

import com.abzed.template.auth.EmailService;
import com.abzed.template.auth.EmailVerificationTokenRepository;
import com.abzed.template.auth.PasswordResetTokenRepository;
import com.abzed.template.auth.RefreshTokenRepository;
import com.abzed.template.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockBean
    private EmailService emailService;

    @BeforeEach
    void setup() {
        Mockito.doNothing().when(emailService).send(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void fullAuthFlow_register_verify_login_refresh_updatePassword() throws Exception {
        String email = "auth.flow@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Register("Auth Flow", email, "Pass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String verifyToken = emailVerificationTokenRepository
                .findTopByUserEmailOrderByExpiryDateDesc(email)
                .orElseThrow()
                .getToken();

        mockMvc.perform(post("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Login(email, "Pass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        String accessCookie = extractCookie(loginResult, "accessToken");
        String refreshCookie = extractCookie(loginResult, "refreshToken");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", cookieValue(refreshCookie))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/update-password")
                        .cookie(new Cookie("accessToken", cookieValue(accessCookie)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.UpdatePassword("Pass123!", "Pass456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Login(email, "Pass456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void forgotAndResetPasswordFlow() throws Exception {
        String email = "reset.flow@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Register("Reset Flow", email, "Pass123!"))))
                .andExpect(status().isOk());

        String verifyToken = emailVerificationTokenRepository
                .findTopByUserEmailOrderByExpiryDateDesc(email)
                .orElseThrow()
                .getToken();

        mockMvc.perform(post("/api/auth/verify-email").param("token", verifyToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Forgot(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        String resetToken = passwordResetTokenRepository
                .findTopByUserEmailOrderByExpiryDateDesc(email)
                .orElseThrow()
                .getToken();

        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Reset(resetToken, "Pass999!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Payload.Login(email, "Pass999!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private String extractCookie(MvcResult result, String cookieName) {
        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        return setCookies.stream()
                .filter(value -> value.startsWith(cookieName + "="))
                .map(value -> value.split(";", 2)[0])
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing cookie: " + cookieName));
    }

    private String cookieValue(String cookiePair) {
        int idx = cookiePair.indexOf('=');
        if (idx < 0) {
            return cookiePair;
        }
        return cookiePair.substring(idx + 1);
    }

    private static class Payload {
        record Register(String fullName, String email, String password) {
        }

        record Login(String email, String password) {
        }

        record Forgot(String email) {
        }

        record Reset(String token, String newPassword) {
        }

        record UpdatePassword(String currentPassword, String newPassword) {
        }
    }
}
