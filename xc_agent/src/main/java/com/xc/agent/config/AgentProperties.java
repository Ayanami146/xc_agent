package com.xc.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Java 业务服务调用 Python Agent 时使用的最小配置。
 *
 * <p>这些配置只描述当前聊天链路真正需要的内容。内部 Token 为空时不发送
 * {@code X-Internal-Token}，便于本地开发；部署环境开启 Python 内部鉴权后，
 * 双方通过同一个环境变量注入 Token，禁止把真实密钥写入配置文件。</p>
 */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(
        String baseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration streamTimeout,
        String modelRoute,
        List<String> knowledgeBaseIds,
        int maxOutputTokens
) {
}
