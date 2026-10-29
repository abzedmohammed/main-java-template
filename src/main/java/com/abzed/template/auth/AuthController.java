package com.abzed.template.auth;

import com.abzed.template.common.ApiResponse;
import com.abzed.template.user.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sign_up")
    public ResponseEntity<ApiResponse<AuthInitResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Verification code sent", authService.signUp(request)));
    }

    @PostMapping("/sign_in")
    public ResponseEntity<ApiResponse<AuthInitResponse>> signIn(@Valid @RequestBody SignInRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Verification code sent", authService.signIn(request)));
    }

    @PostMapping("/sign_in_otp")
    public ResponseEntity<ApiResponse<Object>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthSessionResponse session = authService.verifyOtp(request);
        if (session == null) {
            // Password-reset flow: verified, no session issued.
            return ResponseEntity.ok(ApiResponse.success("OTP verified", null));
        }
        return ResponseEntity.ok(ApiResponse.<Object>success("Login successful", session));
    }

    @PostMapping("/resend_otp")
    public ResponseEntity<ApiResponse<Object>> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Verification code resent", null));
    }

    @PostMapping("/reset_password")
    public ResponseEntity<ApiResponse<AuthInitResponse>> resetPassword(@Valid @RequestBody ResetPasswordInitRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Verification code sent", authService.initiatePasswordReset(request)));
    }

    @PostMapping("/update_password")
    public ResponseEntity<ApiResponse<Object>> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        authService.updatePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password updated successfully", null));
    }

    @PostMapping("/refresh-session")
    public ResponseEntity<ApiResponse<RefreshSessionResponse>> refreshSession(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshSession(principal.getUser())));
    }
}
