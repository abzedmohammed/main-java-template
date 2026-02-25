package com.abzed.template.auth;

import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.user.AuthProvider;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SystemLogService systemLogService;

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL);

        User saved = userRepository.save(user);
        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "User registered",
                "A new local user account was created", saved.getEmail(), "SUCCESS");

        return saved;
    }

    public AuthTokens login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        refreshTokenService.revokeAllForUser(user.getId());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        refreshTokenService.create(user, refreshToken, jwtService.getRefreshExpiry(refreshToken));

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "User login",
                "User authenticated successfully", user.getEmail(), "SUCCESS");

        return new AuthTokens(accessToken, refreshToken);
    }

    public String refresh(String refreshToken) {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        RefreshToken token = refreshTokenService.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (token.isRevoked() || token.getExpiryDate().isBefore(java.time.Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        UUID userId = jwtService.extractUserIdFromRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return jwtService.generateAccessToken(user);
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        refreshTokenService.findByToken(refreshToken).ifPresent(token -> {
            String actor = token.getUser() != null ? token.getUser().getEmail() : "Unknown";
            refreshTokenService.revoke(token);
            systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "User logout",
                    "Refresh token revoked and session terminated", actor, "SUCCESS");
        });
    }

    public void updatePassword(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password updated",
                "User updated account password and existing refresh tokens were revoked",
                user.getEmail(), "SUCCESS");
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
