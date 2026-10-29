package com.abzed.template.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Starts the forgot-password flow. Always succeeds (no account enumeration). */
public record ResetPasswordInitRequest(
        @Email @NotBlank String email
) {
}
