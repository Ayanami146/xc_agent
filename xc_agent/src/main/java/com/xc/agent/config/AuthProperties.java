package com.xc.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration smsCodeTtl,
        String jwtSecret,
        String refreshCookieName,
        String refreshCookiePath,
        boolean cookieSecure
) {
}
