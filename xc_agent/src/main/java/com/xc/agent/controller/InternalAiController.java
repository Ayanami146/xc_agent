package com.xc.agent.controller;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.config.AgentProperties;
import com.xc.agent.model.vo.internal.InternalVOs;
import com.xc.agent.service.content.ManualRagManifestService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Python Agent 反向读取 Java 业务真相源的内部接口。
 *
 * <p>该路径不经过用户 JWT 拦截器，只允许双方约定的内部 Token。开发环境未配置 Token
 * 时允许本机联调；生产环境必须配置与 Agent 相同的随机长令牌。</p>
 */
@RestController
@RequestMapping("/internal/rag/v1")
public class InternalAiController {
    private final ManualRagManifestService manifestService;
    private final AgentProperties agentProperties;

    public InternalAiController(ManualRagManifestService manifestService,
                                AgentProperties agentProperties) {
        this.manifestService = manifestService;
        this.agentProperties = agentProperties;
    }

    @GetMapping("/manuals")
    public List<InternalVOs.RagManualVO> manuals(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        verifyInternalToken(token);
        return manifestService.listPublishedManuals();
    }

    private void verifyInternalToken(String actual) {
        String expected = agentProperties.internalToken();
        if (expected == null || expected.isBlank()) {
            return;
        }
        if (actual == null || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("INTERNAL_AUTH_INVALID", 401, "内部服务认证失败");
        }
    }
}
