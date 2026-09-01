package com.xc.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.faq-cache")
public record FaqCacheProperties(Duration ttl, String keyPrefix) {
}
