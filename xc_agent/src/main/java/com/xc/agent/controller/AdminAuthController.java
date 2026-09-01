package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.config.AdminProperties;
import com.xc.agent.config.AuthProperties;
import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.model.vo.auth.AuthVOs;
import com.xc.agent.service.AdminAuthService;
import com.xc.agent.service.admin.AdminAuthModels;
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
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AdminAuthService authService;
    private final AdminProperties adminProperties;
    private final AuthProperties authProperties;

    public AdminAuthController(AdminAuthService authService, AdminProperties adminProperties,
                               AuthProperties authProperties) {
        this.authService = authService;
        this.adminProperties = adminProperties;
        this.authProperties = authProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthVOs.AdminAuthSessionVO>> login(
            @Valid @RequestBody AdminDTOs.AdminLoginDTO request, HttpServletRequest servletRequest) {
        return sessionResponse(authService.login(request, metadata(servletRequest)), servletRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthVOs.AdminAuthSessionVO>> refresh(HttpServletRequest request) {
        return sessionResponse(authService.refresh(cookieValue(request)), request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookieValue(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", true).toString()).build();
    }

    private ResponseEntity<ApiResponse<AuthVOs.AdminAuthSessionVO>> sessionResponse(
            AdminAuthModels.AdminAuthResult result, HttpServletRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), false).toString())
                .body(new ApiResponse<>(RequestIdFilter.get(request), result.session()));
    }

    private ResponseCookie cookie(String value, boolean clear) {
        return ResponseCookie.from(adminProperties.refreshCookieName(), value)
                .httpOnly(true).secure(authProperties.cookieSecure()).sameSite("Lax")
                .path(adminProperties.refreshCookiePath())
                .maxAge(clear ? java.time.Duration.ZERO : authProperties.refreshTokenTtl()).build();
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (adminProperties.refreshCookieName().equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private AuthModels.LoginMetadata metadata(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        return new AuthModels.LoginMetadata(RequestIdFilter.get(request), ip, request.getHeader("User-Agent"));
    }
}
