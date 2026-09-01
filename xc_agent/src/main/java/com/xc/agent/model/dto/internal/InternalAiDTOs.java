package com.xc.agent.model.dto.internal;

import java.util.List;

/** Java 调用 Python Agent 的内部请求结构，字段名与 Agent camelCase 契约一致。 */
public final class InternalAiDTOs {
    private InternalAiDTOs() {
    }

    /**
     * 当前运行策略由可信的 Java 服务生成，浏览器不能覆盖模型路由和知识库范围。
     */
    public record AgentPolicyDTO(
            String modelRoute,
            List<String> knowledgeBaseIds,
            boolean toolsEnabled,
            int maxOutputTokens
    ) {
    }

    /** history 保留为 null，由 Agent 自己从 Redis/MongoDB 恢复上下文。 */
    public record AgentChatStreamDTO(
            Long requestId,
            Long sessionId,
            Long userId,
            String message,
            Object history,
            AgentPolicyDTO policy
    ) {
    }
}
