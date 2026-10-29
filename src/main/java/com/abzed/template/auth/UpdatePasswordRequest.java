package com.abzed.template.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Saves a new password after a verified password-reset OTP. Pre-auth: authorized
 * by a recently verified reset OTP for {@code usrId}, not by a session.
 */
public record UpdatePasswordRequest(
        @NotBlank String reqId,
        @NotBlank String password
) {
}
