package com.abzed.template.auth;

import jakarta.validation.constraints.NotBlank;

public record UpdatePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
