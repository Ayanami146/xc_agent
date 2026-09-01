package com.xc.agent.model.dto.admin;

import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.enums.TicketEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AdminDTOs {
    private AdminDTOs() {
    }

    public record AdminLoginDTO(@NotBlank String account, @NotBlank String password) {
    }

    public record MfaVerifyDTO(@NotBlank String mfaTicket, @NotBlank @Size(min = 6, max = 12) String code) {
    }

    public record AdminUserQueryDTO(String keyword, AuthEnums.UserStatus status, @Min(1) Integer page,
                                    @Min(1) @Max(100) Integer pageSize) {
    }

    public record AdminUserSaveDTO(@NotBlank String account, @NotBlank String name,
                                   @NotEmpty List<String> roleIds, AuthEnums.UserStatus status) {
    }

    public record RoleSaveDTO(@NotBlank String name, @NotEmpty List<String> permissionCodes) {
    }

    public record ReasonDTO(@NotBlank @Size(max = 1000) String reason) {
    }

    public record AdminTicketQueryDTO(String keyword, TicketEnums.Status status, String assigneeId,
                                      LocalDate from, LocalDate to, @Min(1) Integer page,
                                      @Min(1) @Max(100) Integer pageSize) {
    }

    public record TicketAssignDTO(@NotBlank String assigneeId) {
    }

    public record TicketTransitionDTO(@NotNull TicketEnums.Status targetStatus,
                                      @NotBlank @Size(max = 1000) String reason) {
    }

    public record AdminTicketReplyDTO(@NotBlank @Size(max = 2000) String content,
                                      @Size(max = 5) List<String> attachmentIds) {
    }

    public record ConvertToFaqDTO(@NotBlank String categoryId, @NotBlank @Size(max = 255) String title,
                                  @NotBlank @Size(max = 1000) String question,
                                  @NotBlank @Size(max = 10000) String answer) {
    }

    public record CategorySaveDTO(@NotBlank @Size(max = 100) String name, Integer sortOrder,
                                  String status) {
    }

    public record CategoryOrderItemDTO(@NotBlank String id, @NotNull Integer sortOrder,
                                       @NotNull Integer version) {
    }

    public record CategoryOrderDTO(@NotEmpty List<CategoryOrderItemDTO> items) {
    }

    public record FaqSaveDTO(@NotBlank String categoryId, @NotBlank String title,
                             @NotBlank String question, @NotBlank String answer,
                             String summary, String keywords, Boolean top) {
    }

    public record ManualSaveDTO(@NotBlank String categoryId, @NotBlank String title,
                                String summary) {
    }

    public record PublishActionDTO(@NotBlank @Size(max = 1000) String reason) {
    }

    public record KnowledgeBaseSaveDTO(@NotBlank String name, String description, String status) {
    }

    public record KnowledgeIndexDTO(String indexConfigVersion) {
    }

    public record KnowledgeEvaluationDTO(@NotBlank @Size(max = 8000) String question,
                                         String modelRoute, List<String> knowledgeBaseIds,
                                         Boolean toolsEnabled) {
    }

    public record PromptSaveDTO(@NotBlank String sceneKey, @NotBlank String name) {
    }

    public record PromptVersionSaveDTO(@NotBlank @Size(max = 50000) String content,
                                       Map<String, Object> variablesSchema) {
    }

    public record PromptRollbackDTO(@NotBlank String targetVersionId,
                                    @NotBlank @Size(max = 1000) String reason) {
    }

    public record ModelProviderSaveDTO(@NotBlank String code, @NotBlank String baseUrl,
                                       Boolean enabled, Map<String, Object> timeoutConfig) {
    }

    public record ModelSecretDTO(@NotBlank String secret, @NotBlank @Size(max = 1000) String reason) {
    }

    public record ModelDefinitionSaveDTO(@NotBlank String providerId, @NotBlank String modelName,
                                         @NotBlank String purpose, Integer contextWindow,
                                         Boolean enabled) {
    }

    public record ModelRouteSaveDTO(@NotBlank String routeKey, @NotBlank String primaryModelId,
                                    String fallbackModelId, @Min(1) Integer concurrencyLimit,
                                    Map<String, Object> params) {
    }

    public record BudgetSaveDTO(BigDecimal dailyBudget, BigDecimal monthlyBudget) {
    }

    public record ToolSaveDTO(@NotBlank String toolKey, @NotBlank String description,
                              @NotNull Map<String, Object> inputSchema, @NotBlank String adapterType,
                              @Min(1) Integer timeoutMs, @Min(1) Integer maxResultBytes,
                              Boolean enabled) {
    }

    public record StatisticsQueryDTO(LocalDate from, LocalDate to, String dimension) {
    }

    public record ExportCreateDTO(@NotBlank String reportType, LocalDate from, LocalDate to,
                                  Map<String, Object> filters) {
    }

    public record AuditQueryDTO(String operatorId, String resourceType, String action,
                                String requestId, LocalDate from, LocalDate to,
                                @Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
    }
}
