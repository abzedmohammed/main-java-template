package com.abzed.template.auth;

import com.abzed.template.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    private final AuthProperties authProperties;

    public CookieUtil(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public ResponseCookie accessCookie(String token, long maxAgeSeconds) {
        return build(authProperties.getCookie().getAccessName(), token, maxAgeSeconds);
    }

    public ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return build(authProperties.getCookie().getRefreshName(), token, maxAgeSeconds);
    }

    public ResponseCookie clearAccessCookie() {
        return build(authProperties.getCookie().getAccessName(), "", 0);
    }

    public ResponseCookie clearRefreshCookie() {
        return build(authProperties.getCookie().getRefreshName(), "", 0);
    }

    private ResponseCookie build(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(authProperties.getCookie().isHttpOnly())
                .secure(authProperties.getCookie().isSecure())
                .sameSite(authProperties.getCookie().getSameSite())
                .path(authProperties.getCookie().getPath())
                .maxAge(maxAgeSeconds)
                .build();
    }
}
