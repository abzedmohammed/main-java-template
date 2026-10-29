package com.abzed.template;

import com.abzed.template.auth.OtpTokenRepository;
import com.abzed.template.auth.PasswordResetTokenRepository;
import com.abzed.template.auth.ratelimit.LoginRateLimitRepository;
import com.abzed.template.template.EmailService;
import com.abzed.template.template.MessagingService;
import com.abzed.template.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private LoginRateLimitRepository loginRateLimitRepository;

    /** Mocked at the SMTP boundary; the real MessagingService + seeded templates still run. */
    @MockitoBean
    private EmailService emailService;

    @MockitoSpyBean
    private MessagingService messagingService;

    @BeforeEach
    void setup() {
        otpTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        loginRateLimitRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signUpReturnsReqIdMaskedEmailAndOtpTimer() throws Exception {
        postJson("/auth/sign_up", new Payload.SignUp("Jane Doe", "0711000001", "jane@example.com", "Pass123!", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.result.reqId").isNotEmpty())
                .andExpect(jsonPath("$.data.result.maskedEmail").value("ja****@example.com"))
                .andExpect(jsonPath("$.data.result.otpTimer").value(5));
    }

    @Test
    void registrationOtpActivatesTheAccountAndStartsASession() throws Exception {
        String reqId = signUp("reg.flow@example.com", "0711000002");

        postJson("/auth/sign_in_otp", new Payload.Otp(reqId, lastEmailedOtp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.result.user.usrEmail").value("reg.flow@example.com"))
                .andExpect(jsonPath("$.data.result.user.usrRoleCode").value("USER"))
                .andExpect(jsonPath("$.data.result.user.usrPasswordHash").doesNotExist());

        // Registration completed: the seeded WELCOME template renders and goes out as HTML.
        Mockito.verify(emailService).sendHtml(Mockito.eq("reg.flow@example.com"),
                Mockito.eq("Welcome to Main Java Template"), Mockito.contains("Your account is ready"));
    }

    @Test
    void unverifiedAccountCannotSignIn() throws Exception {
        signUp("draft.user@example.com", "0711000003");

        postJson("/auth/sign_in", new Payload.SignIn("draft.user@example.com", "Pass123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please verify account before continuing"));
    }

    @Test
    void signInRequiresAnOtpBeforeIssuingAToken() throws Exception {
        registerAndActivate("login.flow@example.com", "0711000004");

        String reqId = readResult(postJson("/auth/sign_in", new Payload.SignIn("login.flow@example.com", "Pass123!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.otpTimer").value(5))
                .andExpect(jsonPath("$.data.result.accessToken").doesNotExist()))
                .get("reqId").asText();

        postJson("/auth/sign_in_otp", new Payload.Otp(reqId, lastEmailedOtp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.accessToken").isNotEmpty());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        registerAndActivate("wrong.pass@example.com", "0711000005");

        postJson("/auth/sign_in", new Payload.SignIn("wrong.pass@example.com", "Nope123!"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void wrongOtpIsRejected() throws Exception {
        String reqId = signUp("wrong.otp@example.com", "0711000006");
        String wrong = "0000".equals(lastEmailedOtp()) ? "1111" : "0000";

        postJson("/auth/sign_in_otp", new Payload.Otp(reqId, wrong))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired code"));
    }

    @Test
    void duplicateEmailOfAnActiveAccountIsAConflict() throws Exception {
        registerAndActivate("taken@example.com", "0711000007");

        postJson("/auth/sign_up", new Payload.SignUp("Other", "0711000099", "taken@example.com", "Pass123!", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void forgotPasswordFlowResetsThePassword() throws Exception {
        registerAndActivate("reset.flow@example.com", "0711000008");

        String reqId = readResult(postJson("/auth/reset_password", new Payload.Email("reset.flow@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.otpTimer").value(5)))
                .get("reqId").asText();

        // Reset OTP verifies without issuing a session.
        postJson("/auth/sign_in_otp", new Payload.Otp(reqId, lastEmailedOtp()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").doesNotExist());

        postJson("/auth/update_password", new Payload.UpdatePassword(reqId, "Pass999!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        postJson("/auth/sign_in", new Payload.SignIn("reset.flow@example.com", "Pass999!"))
                .andExpect(status().isOk());
    }

    @Test
    void updatePasswordWithoutAVerifiedResetOtpIsRejected() throws Exception {
        registerAndActivate("no.reset@example.com", "0711000009");
        String usrId = userRepository.findByUsrEmail("no.reset@example.com").orElseThrow().getUsrId().toString();

        postJson("/auth/update_password", new Payload.UpdatePassword(usrId, "Hijack1!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetForUnknownEmailStillLooksSuccessful() throws Exception {
        postJson("/auth/reset_password", new Payload.Email("ghost@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.reqId").isNotEmpty())
                .andExpect(jsonPath("$.data.result.maskedEmail").value("gh****@example.com"))
                .andExpect(jsonPath("$.data.result.otpTimer").value(5));
    }

    @Test
    void refreshSessionIssuesANewTokenForAnAuthenticatedUser() throws Exception {
        String accessToken = registerAndActivate("refresh.flow@example.com", "0711000010");

        mockMvc.perform(post("/auth/refresh-session").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.token").isNotEmpty());
    }

    @Test
    void refreshSessionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/auth/refresh-session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void systemLogsAreForbiddenForNonAdminUsers() throws Exception {
        String accessToken = registerAndActivate("normal.user@example.com", "0711000011");

        mockMvc.perform(get("/api/system-logs").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    /** Sign up + verify the registration OTP; returns the session access token. */
    private String registerAndActivate(String email, String phone) throws Exception {
        String reqId = signUp(email, phone);
        return readResult(postJson("/auth/sign_in_otp", new Payload.Otp(reqId, lastEmailedOtp()))
                .andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    private String signUp(String email, String phone) throws Exception {
        return readResult(postJson("/auth/sign_up", new Payload.SignUp("Test User", phone, email, "Pass123!", true))
                .andExpect(status().isOk()))
                .get("reqId").asText();
    }

    /** The OTP only exists in the delivered email, so read it from the last OTP email sent. */
    @SuppressWarnings("unchecked")
    private String lastEmailedOtp() {
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(messagingService, Mockito.atLeastOnce())
                .sendTemplatedEmail(Mockito.anyString(), Mockito.startsWith("OTP_"), vars.capture());
        List<Map<String, Object>> sent = vars.getAllValues();
        return (String) sent.get(sent.size() - 1).get("otpCode");
    }

    private ResultActions postJson(String path, Object body) throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode readResult(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
                .get("data").get("result");
    }

    private static class Payload {
        record SignUp(String fullName, String phone, String email, String password, boolean agreement) {
        }

        record SignIn(String email, String secret) {
        }

        record Otp(String reqId, String otp) {
        }

        record Email(String email) {
        }

        record UpdatePassword(String reqId, String password) {
        }
    }
}
