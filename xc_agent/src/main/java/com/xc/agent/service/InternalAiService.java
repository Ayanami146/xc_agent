package com.xc.agent.service;

import tools.jackson.databind.JsonNode;
import com.xc.agent.model.dto.internal.InternalAiDTOs;

import java.util.function.Consumer;

/** Java 到 Python Agent 的内部 HTTP/SSE 连接器。 */
public interface InternalAiService {
    /**
     * 执行一条 Agent 流，并按接收顺序回调完整的内部事件。
     * 方法在读到 done/error 后返回；若连接提前结束则抛出安全的连接异常。
     */
    void stream(InternalAiDTOs.AgentChatStreamDTO request,
                Consumer<AgentEvent> eventConsumer);

    /** 幂等地请求 Python Agent 取消指定运行。 */
    void cancel(Long requestId);

    /** payload 保留为 JsonNode，避免 Java 重复定义 Python 的内部扩展字段。 */
    record AgentEvent(String event, JsonNode payload) {
    }
}
