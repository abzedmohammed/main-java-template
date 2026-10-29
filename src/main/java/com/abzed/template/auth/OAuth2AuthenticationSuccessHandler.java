package com.abzed.template.auth;

import com.abzed.template.common.SystemLogLevel;
import com.abzed.template.common.SystemLogService;
import com.abzed.template.user.AuthProvider;
import com.abzed.template.user.User;
import com.abzed.template.user.UserRepository;
import com.abzed.template.user.UserRole;
import com.abzed.template.user.UserStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Social login success: upsert the user, mint an access token, and redirect to the
 * frontend success URL with the token as a query param (token-in-body model — no cookies).
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
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

        User user = userRepository.findByUsrEmail(email).orElseGet(() -> {
            User u = new User();
            u.setUsrEmail(email);
            u.setUsrFullName(name == null ? "OAuth User" : name);
            u.setUsrProvider(AuthProvider.GOOGLE);
            u.setUsrProviderId(providerId);
            u.setUsrRole(UserRole.USER);
            u.setUsrEmailVerified(true);
            u.setUsrStatus(UserStatus.ACTIVE);
            return userRepository.save(u);
        });

        String accessToken = jwtService.generateAccessToken(user);

        systemLogService.log(SystemLogLevel.SECURITY, "AUTH", "OAuth login",
                "User authenticated via social provider", user.getUsrEmail(), "SUCCESS");

        String redirect = UriComponentsBuilder.fromUriString(oauthSuccessUrl)
                .queryParam("token", accessToken)
                .build()
                .toUriString();
        response.sendRedirect(redirect);
    }
}
