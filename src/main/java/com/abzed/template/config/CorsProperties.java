package com.abzed.template.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized CORS configuration. Override {@code app.cors.allowed-origins}
 * per environment (comma-separated) instead of editing {@code SecurityConfig}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** Origin patterns allowed to call the API. */
    private List<String> allowedOrigins = List.of("http://localhost:*");

    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "Accept");

    private List<String> exposedHeaders = List.of("Set-Cookie", "Authorization");

    private boolean allowCredentials = true;
}
