package com.abzed.template.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Registration request. Credential fields are intentionally un-prefixed per the
 * frontend auth contract; {@code agreement} is the required Terms/Privacy acceptance.
 */
public record SignUpRequest(
        @NotBlank String fullName,
        @NotBlank String phone,
        @Email @NotBlank String email,
        @NotBlank String password,
        @AssertTrue(message = "You must accept the Terms and Privacy Policy") boolean agreement
) {
}
