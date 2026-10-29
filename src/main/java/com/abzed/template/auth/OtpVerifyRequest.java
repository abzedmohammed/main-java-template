package com.abzed.template.auth;

import jakarta.validation.constraints.NotBlank;

public record OtpVerifyRequest(
        @NotBlank(message = "Request cannot be verified. Please try again later") String reqId,
        @NotBlank String otp
) {
}
