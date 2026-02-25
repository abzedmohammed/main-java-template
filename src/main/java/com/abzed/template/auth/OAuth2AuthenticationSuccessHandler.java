package com.abzed.template.auth;

import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.user.AuthProvider;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtil cookieUtil;
    private final SystemLogService systemLogService;

    @Value("${app.frontend.oauth-success-url:http://localhost:5173/oauth/success}")
    private String oauthSuccessUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String providerId = oauth2User.getName();

        if (email == null || email.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing email from OAuth provider");
            return;
        }

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFullName(name == null ? "OAuth User" : name);
            u.setProvider(AuthProvider.GOOGLE);
            u.setProviderId(providerId);
            u.setRole(UserRole.USER);
            u.setEmailVerified(true);
            return userRepository.save(u);
        });

        refreshTokenService.revokeAllForUser(user.getId());

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        refreshTokenService.create(user, refreshToken, jwtService.getRefreshExpiry(refreshToken));

        response.addHeader("Set-Cookie", cookieUtil.accessCookie(accessToken, 15 * 60).toString());
        response.addHeader("Set-Cookie", cookieUtil.refreshCookie(refreshToken, 7 * 24 * 60 * 60).toString());

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "OAuth login",
                "User authenticated via social provider", user.getEmail(), "SUCCESS");

        response.sendRedirect(oauthSuccessUrl);
    }
}
