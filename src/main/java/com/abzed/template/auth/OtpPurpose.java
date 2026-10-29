package com.abzed.template.auth;

/**
 * The pre-auth flow an OTP belongs to. Shared by the login, registration, and
 * password-reset flows, all of which verify via {@code POST /auth/sign_in_otp}.
 */
public enum OtpPurpose {
    LOGIN,
    REGISTRATION,
    PASSWORD_RESET
}
