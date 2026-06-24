package com.abzed.template.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private Cookie cookie = new Cookie();
    private Jwt jwt = new Jwt();
    private Social social = new Social();

    @Getter
    @Setter
    public static class Cookie {
        private String accessName = "accessToken";
        private String refreshName = "refreshToken";
        private String sameSite = "Lax";
        private String path = "/";
        private boolean secure = false;
        private boolean httpOnly = true;
    }

    @Getter
    @Setter
    public static class Jwt {
        private String accessSecret = "replace-with-base64-secret";
        private String refreshSecret = "replace-with-base64-secret";
        private long accessMinutes = 15;
        private long refreshDays = 7;
    }

    @Getter
    @Setter
    public static class Social {
        private boolean enabled = false;
    }
}
