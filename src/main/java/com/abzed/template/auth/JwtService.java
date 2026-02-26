package com.abzed.template.auth;

import com.abzed.template.config.AuthProperties;
import com.abzed.template.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final AuthProperties authProperties;

    public JwtService(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claims(Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "name", user.getFullName()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(authProperties.getJwt().getAccessMinutes(), ChronoUnit.MINUTES)))
                .signWith(accessKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(authProperties.getJwt().getRefreshDays(), ChronoUnit.DAYS)))
                .signWith(refreshKey())
                .compact();
    }

    public UUID extractUserIdFromAccessToken(String token) {
        return UUID.fromString(parse(token, accessKey()).getSubject());
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        return UUID.fromString(parse(token, refreshKey()).getSubject());
    }

    public Instant getRefreshExpiry(String token) {
        return parse(token, refreshKey()).getExpiration().toInstant();
    }

    public boolean validateAccessToken(String token) {
        parse(token, accessKey());
        return true;
    }

    public boolean validateRefreshToken(String token) {
        parse(token, refreshKey());
        return true;
    }

    private Claims parse(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey accessKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(authProperties.getJwt().getAccessSecret()));
    }

    private SecretKey refreshKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(authProperties.getJwt().getRefreshSecret()));
    }
}
