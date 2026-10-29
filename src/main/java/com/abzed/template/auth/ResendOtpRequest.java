package com.abzed.template.auth;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
        @NotBlank String reqId
) {
}
