package com.abzed.template.auth;

import com.abzed.template.config.AuthProperties;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    // 32-byte base64-encoded keys (HMAC-SHA256 minimum).
    private static final String ACCESS_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_SECRET = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setup() {
        jwtService = new JwtService(props(ACCESS_SECRET));

        user = new User();
        user.setUsrId(UUID.randomUUID());
        user.setUsrEmail("jwt.user@example.com");
        user.setUsrFullName("Jwt User");
        user.setUsrRole(UserRole.USER);
    }

    @Test
    void accessTokenRoundTripsTheUserId() {
        String token = jwtService.generateAccessToken(user);

        assertTrue(jwtService.validateAccessToken(token));
        assertEquals(user.getUsrId(), jwtService.extractUserIdFromAccessToken(token));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreignToken = new JwtService(props(OTHER_SECRET)).generateAccessToken(user);

        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(foreignToken));
    }

    @Test
    void tamperedTokenIsRejected() {
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken("not-a-real-token"));
    }

    private static AuthProperties props(String secret) {
        AuthProperties props = new AuthProperties();
        props.getJwt().setAccessSecret(secret);
        props.getJwt().setAccessMinutes(15);
        return props;
    }
}
