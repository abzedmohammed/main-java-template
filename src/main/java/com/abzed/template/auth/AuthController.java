package com.abzed.template.auth;

import com.abzed.template.common.ApiResponse;
import com.abzed.template.common.exception.UnauthorizedException;
import com.abzed.template.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.abzed.template.user.UserPrincipal;
import com.abzed.template.user.UserResponse;

import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    private final AuthProperties authProperties;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Object>> register(@Valid @RequestBody RegisterRequest request) {
        var user = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success("User registered", user.getId()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        var tokens = authService.login(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieUtil.accessCookie(tokens.accessToken(), authProperties.getJwt().getAccessMinutes() * 60).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookieUtil.refreshCookie(tokens.refreshToken(), authProperties.getJwt().getRefreshDays() * 24 * 60 * 60).toString())
                .body(ApiResponse.success(new AuthResponse(tokens.accessToken(), "Bearer")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(HttpServletRequest request) {
        String refresh = readCookie(request, authProperties.getCookie().getRefreshName());
        var tokens = authService.refresh(refresh);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieUtil.accessCookie(tokens.accessToken(), authProperties.getJwt().getAccessMinutes() * 60).toString())
                .header(HttpHeaders.SET_COOKIE,
                        cookieUtil.refreshCookie(tokens.refreshToken(), authProperties.getJwt().getRefreshDays() * 24 * 60 * 60).toString())
                .body(ApiResponse.success(new AuthResponse(tokens.accessToken(), "Bearer")));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
        String refresh = readCookie(request, authProperties.getCookie().getRefreshName());
        authService.logout(refresh);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.clearRefreshCookie().toString())
                .body(ApiResponse.success("Logged out", null));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Object>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Object>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.createAndSendResetToken(request.email());
        return ResponseEntity.ok(ApiResponse.success("Password reset instructions sent to email", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset successful", null));
    }

    @PostMapping("/update-password")
    public ResponseEntity<ApiResponse<Object>> updatePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdatePasswordRequest request
    ) {
        authService.updatePassword(principal.getUser(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(principal.getUser())));
    }

    private String readCookie(HttpServletRequest request, String key) {
        if (request.getCookies() == null) {
            throw new UnauthorizedException("Missing authentication cookie");
        }

        return Arrays.stream(request.getCookies())
                .filter(cookie -> key.equals(cookie.getName()))
                .findFirst()
                .map(jakarta.servlet.http.Cookie::getValue)
                .orElseThrow(() -> new UnauthorizedException("Missing authentication cookie"));
    }
}
