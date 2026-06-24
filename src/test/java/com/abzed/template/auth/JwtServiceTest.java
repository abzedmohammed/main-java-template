package com.abzed.template.auth;

import com.abzed.template.config.AuthProperties;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    // 32-byte base64-encoded keys (HMAC-SHA256 minimum).
    private static final String ACCESS_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String REFRESH_SECRET = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setup() {
        AuthProperties props = new AuthProperties();
        props.getJwt().setAccessSecret(ACCESS_SECRET);
        props.getJwt().setRefreshSecret(REFRESH_SECRET);
        props.getJwt().setAccessMinutes(15);
        props.getJwt().setRefreshDays(7);
        jwtService = new JwtService(props);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jwt.user@example.com");
        user.setFullName("Jwt User");
        user.setRole(UserRole.USER);
    }

    @Test
    void accessTokenRoundTripsTheUserId() {
        String token = jwtService.generateAccessToken(user);

        assertTrue(jwtService.validateAccessToken(token));
        assertEquals(user.getId(), jwtService.extractUserIdFromAccessToken(token));
    }

    @Test
    void refreshTokenRoundTripsTheUserIdAndHasFutureExpiry() {
        String token = jwtService.generateRefreshToken(user.getId());

        assertTrue(jwtService.validateRefreshToken(token));
        assertEquals(user.getId(), jwtService.extractUserIdFromRefreshToken(token));
        assertTrue(jwtService.getRefreshExpiry(token).isAfter(Instant.now()));
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String accessToken = jwtService.generateAccessToken(user);

        // Signed with the access key, so refresh-key verification must fail.
        assertThrows(JwtException.class, () -> jwtService.validateRefreshToken(accessToken));
    }

    @Test
    void tamperedTokenIsRejected() {
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken("not-a-real-token"));
    }
}
