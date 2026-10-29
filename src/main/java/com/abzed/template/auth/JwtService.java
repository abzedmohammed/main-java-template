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

/** Stateless access tokens, returned in the body and sent back as {@code Authorization: Bearer}. */
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
                .subject(user.getUsrId().toString())
                .claims(Map.of(
                        "email", user.getUsrEmail(),
                        "role", user.getUsrRole().name(),
                        "name", user.getUsrFullName()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(authProperties.getJwt().getAccessMinutes(), ChronoUnit.MINUTES)))
                .signWith(accessKey())
                .compact();
    }

    public UUID extractUserIdFromAccessToken(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public boolean validateAccessToken(String token) {
        parse(token);
        return true;
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(accessKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey accessKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(authProperties.getJwt().getAccessSecret()));
    }
}
