package com.abzed.template.auth;

import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.common.exception.BadRequestException;
import com.abzed.template.common.exception.ConflictException;
import com.abzed.template.common.exception.ForbiddenException;
import com.abzed.template.common.exception.TooManyRequestsException;
import com.abzed.template.common.exception.UnauthorizedException;
import com.abzed.template.user.AuthProvider;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final SystemLogService systemLogService;
    private final LoginAttemptService loginAttemptService;
    private final EmailVerificationService emailVerificationService;

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email already in use");
        }

        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(false);

        User saved = userRepository.save(user);
        emailVerificationService.createAndSendVerificationToken(saved);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "User registered",
                "A new local user account was created", saved.getEmail(), "SUCCESS");

        return saved;
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        if (loginAttemptService.isBlocked(request.email())) {
            throw new TooManyRequestsException("Account temporarily locked due to too many failed login attempts");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException ex) {
            loginAttemptService.onFailure(request.email());
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (user.getProvider() == AuthProvider.LOCAL && !user.isEmailVerified()) {
            throw new ForbiddenException("Please verify your email before logging in");
        }

        loginAttemptService.onSuccess(request.email());
        refreshTokenService.revokeAllForUser(user.getId());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        refreshTokenService.create(user, refreshToken, jwtService.getRefreshExpiry(refreshToken));

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "User login",
                "User authenticated successfully", user.getEmail(), "SUCCESS");

        return new AuthTokens(accessToken, refreshToken);
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        RefreshToken token = refreshTokenService.findByToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Refresh token not found"));

        if (token.isRevoked() || token.getExpiryDate().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }

        User user = token.getUser();
        refreshTokenService.revoke(token);

        String newAccess = jwtService.generateAccessToken(user);
        String newRefresh = jwtService.generateRefreshToken(user.getId());
        refreshTokenService.create(user, newRefresh, jwtService.getRefreshExpiry(newRefresh));

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Token refreshed",
                "Access token refreshed and refresh token rotated", user.getEmail(), "SUCCESS");

        return new AuthTokens(newAccess, newRefresh);
    }

    @Transactional
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

    @Transactional
    public void updatePassword(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "Password updated",
                "User updated account password and existing refresh tokens were revoked",
                user.getEmail(), "SUCCESS");
    }

    public void verifyEmail(String token) {
        emailVerificationService.verifyEmail(token);
    }

    public record AuthTokens(String accessToken, String refreshToken) {
    }
}
