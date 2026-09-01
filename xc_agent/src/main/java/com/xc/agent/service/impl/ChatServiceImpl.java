package com.xc.agent.service.impl;

import tools.jackson.databind.JsonNode;
import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.security.AuthContext;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.AgentProperties;
import com.xc.agent.mapper.ChatMessageMapper;
import com.xc.agent.mapper.ChatRequestMapper;
import com.xc.agent.mapper.ChatSessionMapper;
import com.xc.agent.mapper.MessageCitationMapper;
import com.xc.agent.mapper.MessageFeedbackMapper;
import com.xc.agent.model.dto.chat.ChatDTOs;
import com.xc.agent.model.dto.internal.InternalAiDTOs;
import com.xc.agent.model.enums.ChatEnums;
import com.xc.agent.model.po.ChatMessageListRow;
import com.xc.agent.model.po.ChatMessagePO;
import com.xc.agent.model.po.ChatRequestPO;
import com.xc.agent.model.po.ChatSessionPO;
import com.xc.agent.model.po.MessageCitationPO;
import com.xc.agent.model.vo.chat.ChatVOs;
import com.xc.agent.model.vo.chat.SseEventVO;
import com.xc.agent.service.ChatService;
import com.xc.agent.service.InternalAiService;
import com.xc.agent.service.ai.InternalAiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 用户聊天业务实现，同时负责 Java 对外 SSE 与 Agent 内部 SSE 的协议转换。
 *
 * <p>一次回答可能持续数十秒，因此绝不能在整个流上开启数据库事务。本类只在“创建占位
 * 记录”和“写入终态”两个阶段使用短事务；中间的 Agent 网络调用在虚拟线程中完成。</p>
 */
@Service
public class ChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    private final ChatSessionMapper chatSessionMapper;
    private final ChatRequestMapper chatRequestMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final MessageCitationMapper messageCitationMapper;
    private final MessageFeedbackMapper messageFeedbackMapper;
    private final InternalAiService internalAiService;
    private final CryptoService cryptoService;
    private final AgentProperties agentProperties;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService chatExecutor;

    public ChatServiceImpl(ChatSessionMapper chatSessionMapper,
                           ChatRequestMapper chatRequestMapper,
                           ChatMessageMapper chatMessageMapper,
                           MessageCitationMapper messageCitationMapper,
                           MessageFeedbackMapper messageFeedbackMapper,
                           InternalAiService internalAiService,
                           CryptoService cryptoService,
                           AgentProperties agentProperties,
                           TransactionTemplate transactionTemplate,
                           @Qualifier("chatExecutor") ExecutorService chatExecutor) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatRequestMapper = chatRequestMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.messageCitationMapper = messageCitationMapper;
        this.messageFeedbackMapper = messageFeedbackMapper;
        this.internalAiService = internalAiService;
        this.cryptoService = cryptoService;
        this.agentProperties = agentProperties;
        this.transactionTemplate = transactionTemplate;
        this.chatExecutor = chatExecutor;
    }

    @Override
    public PageVO<ChatVOs.ChatSessionVO> listSessions(ChatDTOs.SessionQueryDTO query) {
        Long userId = AuthContext.required().userId();
        int page = query.page() == null ? 1 : query.page();
        int pageSize = query.pageSize() == null ? 20 : query.pageSize();
        String keyword = trimToNull(query.keyword());
        int offset = (page - 1) * pageSize;
        List<ChatVOs.ChatSessionVO> items = chatSessionMapper.list(
                offset, pageSize, keyword, userId);
        return new PageVO<>(items, chatSessionMapper.count(keyword, userId), page, pageSize);
    }

    @Override
    public ChatVOs.ChatSessionVO renameSession(Long sessionId, ChatDTOs.SessionRenameDTO dto) {
        requirePositive(sessionId, "CHAT_SESSION_NOT_FOUND", "会话不存在");
        Long userId = AuthContext.required().userId();
        if (chatSessionMapper.rename(sessionId, userId, dto.title().trim()) == 0) {
            throw sessionNotFound();
        }
        ChatVOs.ChatSessionVO session = chatSessionMapper.selectByIdAndUserId(sessionId, userId);
        if (session == null) {
            throw sessionNotFound();
        }
        return session;
    }

    @Override
    public void deleteSession(Long sessionId) {
        requirePositive(sessionId, "CHAT_SESSION_NOT_FOUND", "会话不存在");
        Long userId = AuthContext.required().userId();
        if (chatSessionMapper.softDelete(sessionId, userId) == 0) {
            throw sessionNotFound();
        }
    }

    @Override
    public PageVO<ChatVOs.ChatMessageVO> listMessages(
            Long sessionId, ChatDTOs.MessageQueryDTO query) {
        requirePositive(sessionId, "CHAT_SESSION_NOT_FOUND", "会话不存在");
        Long userId = AuthContext.required().userId();
        if (chatSessionMapper.selectByIdAndUserId(sessionId, userId) == null) {
            throw sessionNotFound();
        }

        int page = query.page() == null ? 1 : query.page();
        int pageSize = query.pageSize() == null ? 50 : query.pageSize();
        int offset = (page - 1) * pageSize;
        List<ChatMessageListRow> rows = chatMessageMapper.list(
                offset, pageSize, sessionId, userId);
        List<Long> messageIds = rows.stream().map(ChatMessageListRow::getId).toList();
        Map<Long, List<ChatVOs.CitationVO>> citationsByMessageId = messageIds.isEmpty()
                ? Map.of()
                : messageCitationMapper.selectByMessageIds(messageIds).stream()
                .collect(Collectors.groupingBy(
                        MessageCitationPO::getMessageId,
                        Collectors.mapping(this::toCitationVO, Collectors.toList())));

        List<ChatVOs.ChatMessageVO> items = rows.stream()
                .map(row -> toMessageVO(
                        row, citationsByMessageId.getOrDefault(row.getId(), List.of())))
                .toList();
        return new PageVO<>(items, chatMessageMapper.count(sessionId), page, pageSize);
    }

    @Override
    public SseEmitter startStream(ChatDTOs.ChatStreamDTO dto) {
        // 必须在离开 Servlet 线程前捕获 userId；AuthContext 是 ThreadLocal，虚拟线程不会继承。
        Long userId = AuthContext.required().userId();
        String question = dto.message().trim();
        ChatExecutionContext context = transactionTemplate.execute(
                status -> initializeRequest(userId, dto.sessionId(), question));
        if (context == null) {
            throw new IllegalStateException("聊天请求初始化事务没有返回结果");
        }

        SseEmitter emitter = new SseEmitter(agentProperties.streamTimeout().toMillis());
        AtomicBoolean clientConnected = new AtomicBoolean(true);
        emitter.onCompletion(() -> clientConnected.set(false));
        emitter.onTimeout(() -> clientConnected.set(false));
        emitter.onError(error -> clientConnected.set(false));
        chatExecutor.submit(() -> runAgentStream(context, emitter, clientConnected));
        return emitter;
    }

    @Override
    public ChatVOs.ChatRequestResultVO getRequestResult(Long requestId) {
        requirePositive(requestId, "CHAT_REQUEST_NOT_FOUND", "聊天请求不存在");
        return buildRequestResult(AuthContext.required().userId(), requestId);
    }

    @Override
    public ChatVOs.ChatRequestResultVO cancelRequest(Long requestId) {
        requirePositive(requestId, "CHAT_REQUEST_NOT_FOUND", "聊天请求不存在");
        Long userId = AuthContext.required().userId();
        ChatRequestPO existing = chatRequestMapper.selectByIdAndUserId(requestId, userId);
        if (existing == null) {
            throw requestNotFound();
        }

        boolean active = "ACCEPTED".equals(existing.getStatus())
                || "RUNNING".equals(existing.getStatus());
        if (active) {
            transactionTemplate.executeWithoutResult(status -> {
                if (chatRequestMapper.markCancelled(requestId, userId) > 0) {
                    chatMessageMapper.updateInterruptedByRequestId(requestId, userId);
                }
            });
            // 数据库先进入取消终态，即使 Agent 临时不可达，稍后的成功事件也无法覆盖取消。
            try {
                internalAiService.cancel(requestId);
            } catch (InternalAiException exception) {
                log.warn("Agent 取消传播失败，requestId={} code={}",
                        requestId, exception.code());
            }
        }
        return buildRequestResult(userId, requestId);
    }

    @Override
    public void saveFeedback(Long messageId, ChatDTOs.MessageFeedbackDTO dto) {
        requirePositive(messageId, "CHAT_MESSAGE_NOT_FOUND", "消息不存在");
        Long userId = AuthContext.required().userId();
        if (chatMessageMapper.selectOwnedAssistantById(messageId, userId) == null) {
            // 不区分消息不存在和属于其他用户，避免通过接口枚举他人消息。
            throw new BusinessException("CHAT_MESSAGE_NOT_FOUND", 404, "消息不存在");
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (dto.feedback() == null) {
                messageFeedbackMapper.delete(messageId, userId);
            } else {
                messageFeedbackMapper.upsert(messageId, userId, dto.feedback().name());
            }
        });
    }

    /** 在一个短事务内创建所有占位记录，保证不会留下半条聊天请求。 */
    private ChatExecutionContext initializeRequest(Long userId,
                                                   Long requestedSessionId,
                                                   String question) {
        Long sessionId = requestedSessionId;
        if (sessionId == null) {
            ChatSessionPO session = ChatSessionPO.builder()
                    .userId(userId)
                    .title(truncate(question, 30))
                    .preview(truncate(question, 255))
                    .build();
            chatSessionMapper.insert(session);
            sessionId = session.getId();
        } else if (chatSessionMapper.selectByIdAndUserId(sessionId, userId) == null) {
            throw sessionNotFound();
        }

        ChatRequestPO request = ChatRequestPO.builder()
                .sessionId(sessionId)
                .userId(userId)
                // request_hash 只满足当前数据审计字段，不承担本轮未实现的通用幂等职责。
                .requestHash(cryptoService.sha256Hex(question))
                .build();
        chatRequestMapper.insert(request);

        ChatMessagePO userMessage = ChatMessagePO.builder()
                .sessionId(sessionId)
                .requestId(request.getId())
                .role(ChatEnums.MessageRole.user.name())
                .status(ChatEnums.MessageStatus.COMPLETED.name())
                .content(question)
                .build();
        chatMessageMapper.insert(userMessage);

        ChatMessagePO assistantMessage = ChatMessagePO.builder()
                .sessionId(sessionId)
                .requestId(request.getId())
                .role(ChatEnums.MessageRole.assistant.name())
                .status(ChatEnums.MessageStatus.STREAMING.name())
                .content("")
                .stage(ChatEnums.Stage.queued.name())
                .build();
        chatMessageMapper.insert(assistantMessage);
        chatSessionMapper.updatePreview(sessionId, userId, truncate(question, 255));
        return new ChatExecutionContext(
                userId, sessionId, request.getId(), userMessage.getId(),
                assistantMessage.getId(), question);
    }

    private void runAgentStream(ChatExecutionContext context,
                                SseEmitter emitter,
                                AtomicBoolean clientConnected) {
        AtomicLong sequence = new AtomicLong(1);
        AgentOutcome outcome = new AgentOutcome();
        sendEvent(emitter, clientConnected, sequence, context.requestId(),
                ChatEnums.StreamEvent.meta,
                new ChatVOs.MetaPayloadVO(
                        context.sessionId(), context.userMessageId(), context.assistantMessageId()));

        if (chatRequestMapper.markRunning(context.requestId(), context.userId()) == 0) {
            // 请求可能在虚拟线程真正开始前已被用户取消，不再启动 Agent。
            safeComplete(emitter);
            return;
        }

        InternalAiDTOs.AgentPolicyDTO policy = new InternalAiDTOs.AgentPolicyDTO(
                agentProperties.modelRoute(), agentProperties.knowledgeBaseIds(),
                false, agentProperties.maxOutputTokens());
        InternalAiDTOs.AgentChatStreamDTO agentRequest = new InternalAiDTOs.AgentChatStreamDTO(
                context.requestId(), context.sessionId(), context.userId(),
                context.question(), null, policy);

        try {
            internalAiService.stream(agentRequest, event -> handleAgentEvent(
                    context.requestId(), event, outcome, emitter, clientConnected, sequence));
            if (outcome.errorCode != null) {
                finishAgentError(context, outcome, emitter, clientConnected, sequence);
                return;
            }
            if (!outcome.done) {
                throw new InternalAiException(
                        "AGENT_STREAM_INTERRUPTED", "智能体响应意外中断", true);
            }
            if (finishSuccess(context, outcome)) {
                sendEvent(emitter, clientConnected, sequence, context.requestId(),
                        ChatEnums.StreamEvent.done,
                        new ChatVOs.DonePayloadVO("stop", context.assistantMessageId()));
            }
        } catch (InternalAiException exception) {
            finishFailure(context, exception.code(), exception.getMessage());
            sendEvent(emitter, clientConnected, sequence, context.requestId(),
                    ChatEnums.StreamEvent.error,
                    new ChatVOs.ErrorPayloadVO(
                            exception.code(), exception.getMessage(), exception.retryable()));
        } catch (RuntimeException exception) {
            log.error("聊天流处理失败，chatRequestId={}", context.requestId(), exception);
            String message = "聊天服务暂时不可用，请稍后重试";
            finishFailure(context, "INTERNAL_ERROR", message);
            sendEvent(emitter, clientConnected, sequence, context.requestId(),
                    ChatEnums.StreamEvent.error,
                    new ChatVOs.ErrorPayloadVO("INTERNAL_ERROR", message, true));
        } finally {
            safeComplete(emitter);
        }
    }

    private void handleAgentEvent(Long requestId,
                                  InternalAiService.AgentEvent event,
                                  AgentOutcome outcome,
                                  SseEmitter emitter,
                                  AtomicBoolean clientConnected,
                                  AtomicLong sequence) {
        switch (event.event()) {
            case "status" -> sendEvent(emitter, clientConnected, sequence, requestId,
                    ChatEnums.StreamEvent.status, event.payload());
            case "delta" -> {
                outcome.answer.append(event.payload().path("content").asText(""));
                sendEvent(emitter, clientConnected, sequence, requestId,
                        ChatEnums.StreamEvent.delta, event.payload());
            }
            case "citation" -> {
                outcome.citations.addAll(parseCitations(event.payload()));
                sendEvent(emitter, clientConnected, sequence, requestId,
                        ChatEnums.StreamEvent.citation, event.payload());
            }
            case "usage" -> {
                outcome.usageJson = event.payload().toString();
                outcome.totalTokens = nullableInt(event.payload(), "totalTokens");
                outcome.modelName = textOrNull(event.payload(), "model");
                sendEvent(emitter, clientConnected, sequence, requestId,
                        ChatEnums.StreamEvent.usage, event.payload());
            }
            case "heartbeat" -> sendEvent(emitter, clientConnected, sequence, requestId,
                    ChatEnums.StreamEvent.heartbeat, event.payload());
            case "done" -> {
                outcome.done = true;
                String model = textOrNull(event.payload(), "model");
                if (model != null) {
                    outcome.modelName = model;
                }
            }
            case "error" -> {
                outcome.errorCode = event.payload().path("code").asText("MODEL_UNAVAILABLE");
                outcome.errorMessage = event.payload().path("message")
                        .asText("智能体服务暂时不可用");
                outcome.retryable = event.payload().path("retryable").asBoolean(true);
            }
            default -> log.debug("忽略 Agent 未使用事件，event={}", event.event());
        }
    }

    private void finishAgentError(ChatExecutionContext context,
                                  AgentOutcome outcome,
                                  SseEmitter emitter,
                                  AtomicBoolean clientConnected,
                                  AtomicLong sequence) {
        if ("RUN_CANCELLED".equals(outcome.errorCode)) {
            transactionTemplate.executeWithoutResult(status -> {
                chatRequestMapper.markCancelled(context.requestId(), context.userId());
                chatMessageMapper.updateInterruptedByRequestId(
                        context.requestId(), context.userId());
            });
        } else {
            finishFailure(context, outcome.errorCode, outcome.errorMessage);
        }
        sendEvent(emitter, clientConnected, sequence, context.requestId(),
                ChatEnums.StreamEvent.error,
                new ChatVOs.ErrorPayloadVO(
                        outcome.errorCode, outcome.errorMessage, outcome.retryable));
    }

    /**
     * 请求状态先从 RUNNING 改为 SUCCEEDED；若用户已取消，更新数为 0，后续消息和引用
     * 不会写入。整个 lambda 仍是一个事务，任何一步失败都会回滚请求终态。
     */
    private boolean finishSuccess(ChatExecutionContext context, AgentOutcome outcome) {
        Boolean result = transactionTemplate.execute(status -> {
            if (chatRequestMapper.markSucceeded(
                    context.requestId(), context.userId(),
                    agentProperties.modelRoute(), outcome.usageJson) == 0) {
                return false;
            }
            if (chatMessageMapper.updateCompleted(
                    context.assistantMessageId(), outcome.answer.toString(),
                    outcome.modelName, outcome.totalTokens) == 0) {
                throw new IllegalStateException("助手占位消息状态不允许完成");
            }
            if (!outcome.citations.isEmpty()) {
                List<MessageCitationPO> rows = new ArrayList<>();
                for (int index = 0; index < outcome.citations.size(); index++) {
                    CitationData citation = outcome.citations.get(index);
                    rows.add(MessageCitationPO.builder()
                            .messageId(context.assistantMessageId())
                            .ordinalNo(index + 1)
                            .sourceId(citation.vo().sourceId())
                            .title(citation.vo().title())
                            .snippet(citation.vo().snippet())
                            .sourceLocator(citation.vo().sourceLocator())
                            .pageNo(citation.vo().page())
                            .score(citation.score())
                            .build());
                }
                messageCitationMapper.insertBatch(rows);
            }
            chatSessionMapper.updatePreview(
                    context.sessionId(), context.userId(), truncate(context.question(), 255));
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private boolean finishFailure(ChatExecutionContext context, String code, String message) {
        Boolean result = transactionTemplate.execute(status -> {
            int changed = chatRequestMapper.markFailed(
                    context.requestId(), context.userId(), code, truncate(message, 500));
            if (changed == 0) {
                return false;
            }
            chatMessageMapper.updateFailed(context.assistantMessageId(), message);
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private ChatVOs.ChatRequestResultVO buildRequestResult(Long userId, Long requestId) {
        ChatRequestPO request = chatRequestMapper.selectByIdAndUserId(requestId, userId);
        if (request == null) {
            throw requestNotFound();
        }
        ChatMessagePO assistant = chatMessageMapper.selectAssistantByRequestIdAndUserId(
                requestId, userId);
        List<ChatVOs.CitationVO> citations = assistant == null
                ? List.of()
                : messageCitationMapper.selectByMessageIds(List.of(assistant.getId())).stream()
                .map(this::toCitationVO)
                .toList();
        ChatVOs.ChatRequestErrorVO error = request.getErrorCode() == null
                ? null
                : new ChatVOs.ChatRequestErrorVO(
                        request.getErrorCode(), request.getErrorMessage());
        return new ChatVOs.ChatRequestResultVO(
                ChatEnums.RequestStatus.valueOf(request.getStatus()),
                request.getSessionId(), assistant == null ? null : assistant.getId(),
                assistant == null ? null : assistant.getContent(), citations, error,
                toInstant(request.getStartedAt()), toInstant(request.getFinishedAt()));
    }

    private List<CitationData> parseCitations(JsonNode payload) {
        List<CitationData> result = new ArrayList<>();
        JsonNode sources = payload.path("sources");
        if (!sources.isArray()) {
            return result;
        }
        for (JsonNode source : sources) {
            ChatVOs.CitationVO vo = new ChatVOs.CitationVO(
                    source.path("title").asText(""), nullableLong(source, "sourceId"),
                    source.path("snippet").asText(""),
                    source.path("sourceLocator").asText(""), nullableInt(source, "page"));
            BigDecimal score = source.hasNonNull("score")
                    ? source.path("score").decimalValue() : null;
            result.add(new CitationData(vo, score));
        }
        return result;
    }

    private void sendEvent(SseEmitter emitter,
                           AtomicBoolean clientConnected,
                           AtomicLong sequence,
                           Long requestId,
                           ChatEnums.StreamEvent event,
                           Object payload) {
        if (!clientConnected.get()) {
            // 浏览器断开不影响后台继续生成和落库，页面可稍后通过结果接口恢复。
            return;
        }
        long currentSequence = sequence.getAndIncrement();
        SseEventVO<Object> envelope = new SseEventVO<>(
                event, requestId, currentSequence, Instant.now(), payload);
        try {
            emitter.send(SseEmitter.event()
                    .name(event.name())
                    .id(Long.toString(currentSequence))
                    .data(envelope));
        } catch (IOException | IllegalStateException exception) {
            clientConnected.set(false);
            log.debug("浏览器 SSE 已断开，后台继续完成聊天请求", exception);
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 容器可能已经因浏览器断开或超时完成 emitter，重复完成无需报错。
        }
    }

    private ChatVOs.ChatMessageVO toMessageVO(
            ChatMessageListRow row, List<ChatVOs.CitationVO> citations) {
        return new ChatVOs.ChatMessageVO(
                row.getId(), row.getRequestId(), row.getRole(), row.getContent(),
                row.getStatus(), row.getCreatedAt().toInstant(ZoneOffset.UTC),
                row.getStage(), citations, row.getFeedback());
    }

    private ChatVOs.CitationVO toCitationVO(MessageCitationPO citation) {
        return new ChatVOs.CitationVO(
                citation.getTitle(), citation.getSourceId(), citation.getSnippet(),
                citation.getSourceLocator(), citation.getPageNo());
    }

    private void requirePositive(Long id, String code, String message) {
        if (id == null || id <= 0) {
            throw new BusinessException(code, 404, message);
        }
    }

    private BusinessException sessionNotFound() {
        return new BusinessException("CHAT_SESSION_NOT_FOUND", 404, "会话不存在");
    }

    private BusinessException requestNotFound() {
        return new BusinessException("CHAT_REQUEST_NOT_FOUND", 404, "聊天请求不存在");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 按 Unicode code point 截断，避免把 emoji 等代理对切成无效字符串。 */
    private String truncate(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private Long nullableLong(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).longValue() : null;
    }

    private Integer nullableInt(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).intValue() : null;
    }

    private String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asText() : null;
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record ChatExecutionContext(
            Long userId, Long sessionId, Long requestId, Long userMessageId,
            Long assistantMessageId, String question) {
    }

    private record CitationData(ChatVOs.CitationVO vo, BigDecimal score) {
    }

    private static final class AgentOutcome {
        private final StringBuilder answer = new StringBuilder();
        private final List<CitationData> citations = new ArrayList<>();
        private boolean done;
        private String usageJson;
        private Integer totalTokens;
        private String modelName;
        private String errorCode;
        private String errorMessage;
        private boolean retryable;
    }
}
