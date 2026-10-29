package com.abzed.template.auth;

/** Completed-session payload returned after a verified login/registration OTP. */
public record AuthSessionResponse(String accessToken, AuthUser user) {
}
