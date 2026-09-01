package com.xc.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        String refreshCookieName,
        String refreshCookiePath,
        int maxLoginAttempts,
        Duration loginLockDuration,
        Path manualStorageDirectory,
        long manualMaxFileSize
) {
}
