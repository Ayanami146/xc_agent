package com.xc.agent.model.dto.internal;

import com.xc.agent.model.enums.ChatEnums;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class InternalDTOs {
    private InternalDTOs() {
    }

    public record HistoryMessageDTO(@NotNull ChatEnums.MessageRole role, @NotBlank String content) {
    }

    public record AiPolicyDTO(@NotBlank String modelRoute, @NotEmpty List<String> knowledgeBaseIds,
                              boolean toolsEnabled, @Min(1) int maxOutputTokens) {
    }

    public record AiChatStreamDTO(
            @NotNull @Min(1) Long requestId,
            @NotNull @Min(1) Long sessionId,
            @NotNull @Min(1) Long userId,
            @NotBlank @Size(max = 8000) String message,
            @Valid List<HistoryMessageDTO> history,
            @NotNull @Valid AiPolicyDTO policy
    ) {
    }

    public record IndexJobDTO(
            @NotBlank String jobId,
            @Min(1) int attemptNo,
            @NotBlank String knowledgeBaseId,
            @NotBlank String documentId,
            @NotBlank String documentVersionId,
            @NotBlank String objectKey,
            @NotBlank String sha256,
            @NotBlank String fileName,
            @NotBlank String indexConfigVersion
    ) {
    }

    public record EvaluationTryDTO(@NotBlank String question, String modelRoute,
                                   List<String> knowledgeBaseIds, Boolean toolsEnabled,
                                   Map<String, Object> overrides) {
    }

    public record IndexJobResultDTO(
            @NotBlank String jobId,
            @Min(1) int attemptNo,
            @NotBlank String status,
            @NotBlank String documentVersionId,
            String collection,
            Integer chunkCount,
            String embeddingModel,
            String indexConfigVersion,
            String errorCode,
            String errorMessage
    ) {
    }

    public record UsageItemDTO(
            @NotBlank String callId,
            @NotNull @Min(1) Long requestId,
            String model,
            @NotBlank String kind,
            @Min(0) int promptTokens,
            @Min(0) int completionTokens,
            BigDecimal estimatedCost,
            @Min(0) long latencyMs,
            @NotBlank String status,
            @NotNull Instant occurredAt
    ) {
    }

    public record UsageBatchDTO(@NotBlank String batchId, @NotEmpty @Valid List<UsageItemDTO> items) {
    }
}
