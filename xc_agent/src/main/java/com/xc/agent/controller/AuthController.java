package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.config.AuthProperties;
import com.xc.agent.model.dto.auth.AuthDTOs;
import com.xc.agent.model.vo.auth.AuthVOs;
import com.xc.agent.service.AuthService;
import com.xc.agent.service.auth.AuthModels;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/sms-codes")
    public ResponseEntity<Void> sendSmsCode(@Valid @RequestBody AuthDTOs.SendSmsCodeDTO request) {
        authService.sendSmsCode(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthVOs.AuthSessionVO>> login(
            @Valid @RequestBody AuthDTOs.LoginDTO request, HttpServletRequest servletRequest) {
        AuthModels.AuthResult result = authService.login(request, metadata(servletRequest));
        return sessionResponse(result, servletRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthVOs.AuthSessionVO>> refresh(HttpServletRequest servletRequest) {
        String token = cookieValue(servletRequest, properties.refreshCookieName());
        AuthModels.AuthResult result = authService.refresh(token);
        return sessionResponse(result, servletRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest servletRequest) {
        String token = cookieValue(servletRequest, properties.refreshCookieName());
        authService.logout(token);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<ApiResponse<AuthVOs.AuthSessionVO>> sessionResponse(
            AuthModels.AuthResult result, HttpServletRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result).toString())
                .body(new ApiResponse<>(RequestIdFilter.get(request), result.session()));
    }

    private ResponseCookie refreshCookie(AuthModels.AuthResult result) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(properties.refreshCookieName(), result.refreshToken())
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(properties.refreshCookiePath());
        if (result.rememberDevice()) {
            builder.maxAge(properties.refreshTokenTtl());
        }
        return builder.build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(properties.refreshCookieName(), "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path(properties.refreshCookiePath())
                .maxAge(0)
                .build();
    }

    private AuthModels.LoginMetadata metadata(HttpServletRequest request) {
        return new AuthModels.LoginMetadata(
                RequestIdFilter.get(request), clientIp(request), request.getHeader("User-Agent"));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String cookieValue(HttpServletRequest request, String configuredName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (configuredName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
