package com.xc.agent.model.vo.chat;

import com.xc.agent.model.enums.ChatEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ChatVOs {
    private ChatVOs() {
    }

    public record CitationVO(String title, Long sourceId, String snippet,
                             String sourceLocator, Integer page) {
    }

    public record ChatSessionVO(Long id, String title, String preview, Instant updatedAt) {
    }

    public record ChatMessageVO(Long id, Long requestId, ChatEnums.MessageRole role,
                                String content, ChatEnums.MessageStatus status,
                                Instant createdAt, ChatEnums.Stage stage,
                                List<CitationVO> citations, ChatEnums.Feedback feedback) {
    }

    public record ChatRequestErrorVO(String code, String message) {
    }

    public record ChatRequestResultVO(ChatEnums.RequestStatus status, Long sessionId,
                                      Long assistantMessageId, String answer,
                                      List<CitationVO> citations, ChatRequestErrorVO error,
                                      Instant startedAt, Instant finishedAt) {
    }

    public record MetaPayloadVO(Long sessionId, Long userMessageId, Long assistantMessageId) {
    }

    public record StatusPayloadVO(ChatEnums.Stage stage, String message) {
    }

    public record DeltaPayloadVO(String content) {
    }

    public record CitationPayloadVO(List<CitationVO> sources) {
    }

    public record UsagePayloadVO(String model, int promptTokens, int completionTokens,
                                 int totalTokens, BigDecimal estimatedCost) {
    }

    public record HeartbeatPayloadVO(Instant serverTime) {
    }

    public record DonePayloadVO(String finishReason, Long messageId) {
    }

    public record ErrorPayloadVO(String code, String message, boolean retryable) {
    }
}
