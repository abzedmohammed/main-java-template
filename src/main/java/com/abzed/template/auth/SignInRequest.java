package com.abzed.template.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SignInRequest(
        @Email @NotBlank String email,
        @NotBlank(message = "Password cannot be empty") String secret
) {
}
