package com.abzed.template.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private Jwt jwt = new Jwt();
    private Otp otp = new Otp();
    private Login login = new Login();
    private Social social = new Social();

    @Getter
    @Setter
    public static class Jwt {
        private String accessSecret = "replace-with-base64-secret";
        private long accessMinutes = 15;
    }

    /** Pre-auth one-time passwords. {@code ttlMinutes} is also returned to the client as {@code otpTimer}. */
    @Getter
    @Setter
    public static class Otp {
        private long ttlMinutes = 5;
        private int maxAttempts = 5;
        /** Server-side HMAC key for hashing OTPs (at least 32 chars). Required at startup. */
        private String secret = "";
    }

    @Getter
    @Setter
    public static class Login {
        private int maxAttempts = 5;
        private long lockMinutes = 15;
    }

    @Getter
    @Setter
    public static class Social {
        private boolean enabled = false;
    }
}
