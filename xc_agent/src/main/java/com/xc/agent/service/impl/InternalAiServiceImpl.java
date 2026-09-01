package com.xc.agent.service.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.xc.agent.config.AgentProperties;
import com.xc.agent.model.dto.internal.InternalAiDTOs;
import com.xc.agent.service.InternalAiService;
import com.xc.agent.service.ai.InternalAiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 基于 JDK HttpClient 的 Python Agent 连接器。
 *
 * <p>Python 返回的是标准 SSE 文本。本类只负责内部协议的发送与解析，不处理 MySQL
 * 状态，也不直接向浏览器发送事件；数据库状态和外部事件顺序始终由 ChatService 负责。</p>
 */
@Service
public class InternalAiServiceImpl implements InternalAiService {
    private static final Logger log = LoggerFactory.getLogger(InternalAiServiceImpl.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AgentProperties properties;
    private final String baseUrl;

    public InternalAiServiceImpl(HttpClient agentHttpClient,
                                 ObjectMapper objectMapper,
                                 AgentProperties properties) {
        this.httpClient = agentHttpClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        // 统一去掉末尾斜杠，避免配置地址与固定路径拼接成双斜杠。
        this.baseUrl = properties.baseUrl().replaceAll("/+$", "");
    }

    @Override
    public void stream(InternalAiDTOs.AgentChatStreamDTO request,
                       Consumer<AgentEvent> eventConsumer) {
        HttpRequest httpRequest;
        try {
            String json = objectMapper.writeValueAsString(request);
            httpRequest = requestBuilder(baseUrl + "/chat/stream")
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .timeout(properties.requestTimeout())
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
        } catch (JacksonException exception) {
            throw new InternalAiException(
                    "AGENT_REQUEST_INVALID", "智能体请求构造失败", false, exception);
        }

        try {
            HttpResponse<InputStream> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String responseBody;
                try (InputStream body = response.body()) {
                    // 只读取有限长度，防止异常代理返回超大错误页占用 Java 堆内存。
                    responseBody = new String(body.readNBytes(8192), StandardCharsets.UTF_8);
                }
                if (response.statusCode() == 422) {
                    // Pydantic 的 422 响应可能包含原始用户输入。日志只保留字段位置、错误
                    // 类型和提示，不记录 input 字段，既方便联调也避免把聊天内容写入日志。
                    log.warn("Agent 请求契约校验失败，requestId={} validation={}",
                            request.requestId(), validationSummary(responseBody));
                    throw new InternalAiException(
                            "AGENT_REQUEST_INVALID", "智能体请求参数无效，请检查服务配置", false);
                }
                log.warn("Agent 流式接口返回非成功状态，requestId={} status={}",
                        request.requestId(), response.statusCode());
                throw new InternalAiException(
                        "MODEL_UNAVAILABLE", "智能体服务暂时不可用", true);
            }

            try (InputStream body = response.body()) {
                parseSse(body, eventConsumer);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InternalAiException(
                    "AGENT_CALL_INTERRUPTED", "智能体调用已中断", true, exception);
        } catch (IOException exception) {
            throw new InternalAiException(
                    "MODEL_UNAVAILABLE", "智能体服务暂时不可用", true, exception);
        }
    }

    @Override
    public void cancel(Long requestId) {
        HttpRequest request = requestBuilder(baseUrl + "/chat/requests/" + requestId + "/cancel")
                .header("Accept", "application/json")
                .timeout(properties.connectTimeout())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new InternalAiException(
                        "AGENT_CANCEL_FAILED", "智能体取消请求未成功", true);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InternalAiException(
                    "AGENT_CANCEL_INTERRUPTED", "智能体取消请求已中断", true, exception);
        } catch (IOException exception) {
            throw new InternalAiException(
                    "AGENT_CANCEL_FAILED", "智能体取消请求未成功", true, exception);
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                // 请求级别再次固定协议，确保测试或其他调用方传入自建 HttpClient 时也不会
                // 意外恢复 h2c Upgrade 行为。
                .version(HttpClient.Version.HTTP_1_1);
        // Python 的开发配置允许关闭鉴权；部署时只需给双方配置相同 Token。
        if (properties.internalToken() != null && !properties.internalToken().isBlank()) {
            builder.header("X-Internal-Token", properties.internalToken());
        }
        return builder;
    }

    /**
     * 从 FastAPI/Pydantic 的 422 响应中提取不含用户输入的校验摘要。
     * 若上游没有返回标准 JSON，则只记录统一占位符，不把未知响应体直接写入日志。
     */
    private String validationSummary(String responseBody) {
        try {
            JsonNode details = objectMapper.readTree(responseBody).path("detail");
            if (!details.isArray()) {
                return "unknown validation error";
            }
            List<String> errors = new ArrayList<>();
            for (JsonNode detail : details) {
                String location = detail.path("loc").toString();
                String type = detail.path("type").asText("validation_error");
                String message = detail.path("msg").asText("invalid value");
                errors.add(location + " " + type + ": " + message);
                if (errors.size() == 5) {
                    break;
                }
            }
            return errors.isEmpty() ? "unknown validation error" : String.join("; ", errors);
        } catch (JacksonException exception) {
            return "unparseable validation response";
        }
    }

    /**
     * 逐行解析 SSE。Agent 当前每个 data 只有一行 JSON，但这里仍支持多行 data，
     * 避免将来 payload 格式化输出后破坏协议。
     */
    private void parseSse(InputStream input,
                          Consumer<AgentEvent> eventConsumer) throws IOException {
        boolean terminalSeen = false;
        String eventName = null;
        StringBuilder data = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() > 0) {
                        String actualEvent = dispatch(eventName, data, eventConsumer);
                        terminalSeen = terminalSeen
                                || "done".equals(actualEvent)
                                || "error".equals(actualEvent);
                    }
                    eventName = null;
                    data.setLength(0);
                    continue;
                }
                if (line.startsWith("event:")) {
                    eventName = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(line.substring("data:".length()).stripLeading());
                }
            }
        }

        // 对没有以空行收尾的合法最后一帧进行兜底解析。
        if (data.length() > 0) {
            String actualEvent = dispatch(eventName, data, eventConsumer);
            terminalSeen = terminalSeen
                    || "done".equals(actualEvent)
                    || "error".equals(actualEvent);
        }
        if (!terminalSeen) {
            throw new InternalAiException(
                    "AGENT_STREAM_INTERRUPTED", "智能体响应意外中断", true);
        }
    }

    private String dispatch(String sseEventName,
                            StringBuilder data,
                            Consumer<AgentEvent> eventConsumer) {
        try {
            JsonNode envelope = objectMapper.readTree(data.toString());
            String actualEvent = envelope.path("event").asText(sseEventName);
            if (actualEvent == null || actualEvent.isBlank()) {
                throw new InternalAiException(
                        "AGENT_PROTOCOL_ERROR", "智能体返回了无法识别的事件", true);
            }
            eventConsumer.accept(new AgentEvent(actualEvent, envelope.path("payload")));
            return actualEvent;
        } catch (JacksonException exception) {
            throw new InternalAiException(
                    "AGENT_PROTOCOL_ERROR", "智能体返回了无效事件", true, exception);
        }
    }
}
