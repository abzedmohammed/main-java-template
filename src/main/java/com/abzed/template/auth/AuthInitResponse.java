package com.abzed.template.auth;

/** Returned by sign-up / sign-in / reset to start an OTP verification step. */
public record AuthInitResponse(String reqId, String maskedEmail, Long otpTimer) {
}
