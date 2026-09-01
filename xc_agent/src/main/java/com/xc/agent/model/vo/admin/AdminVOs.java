package com.xc.agent.model.vo.admin;

import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.enums.ContentEnums;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminVOs {
    private AdminVOs() {
    }

    public record AdminUserVO(String id, String account, String name, List<String> roleIds,
                              AuthEnums.UserStatus status, int version, Instant updatedAt) {
    }

    public record RoleVO(String id, String name, List<String> permissionCodes, int version) {
    }

    public record PermissionVO(String code, String name, String module) {
    }

    public record ContentCategoryVO(String id, String name, int sortOrder,
                                    ContentEnums.EnableStatus status, int version) {
    }

    public record ContentVersionVO(String id, String resourceId, int version,
                                   ContentEnums.PublishStatus status, Map<String, Object> content,
                                   String createdBy, Instant createdAt,
                                   Instant publishedAt) {
    }

    public record KnowledgeBaseVO(String id, String name, String description, String status,
                                  String activeCollectionAlias, int version,
                                  Instant updatedAt) {
    }

    public record IndexJobVO(String jobId, String documentVersionId, int attemptNo,
                             String status, int retryCount, String errorCode,
                             String errorMessage, Instant updatedAt) {
    }

    public record PromptVO(String id, String sceneKey, String name, String currentVersionId,
                           ContentEnums.PublishStatus status, int version) {
    }

    public record PromptVersionVO(String id, String promptId, int versionNo, String content,
                                  Map<String, Object> variablesSchema, String reviewStatus,
                                  Instant publishedAt) {
    }

    public record ModelProviderVO(String id, String code, String baseUrl, boolean enabled,
                                  Map<String, Object> timeoutConfig, int version) {
    }

    public record ModelDefinitionVO(String id, String providerId, String modelName,
                                    String purpose, int contextWindow, boolean enabled,
                                    int version) {
    }

    public record ModelRouteVO(String id, String routeKey, String primaryModelId,
                               String fallbackModelId, int concurrencyLimit,
                               BigDecimal dailyBudget, Map<String, Object> params,
                               int version) {
    }

    public record ToolVO(String id, String toolKey, String description,
                         Map<String, Object> inputSchema, String adapterType,
                         int timeoutMs, int maxResultBytes, boolean enabled, int version) {
    }

    public record StatisticsVO(Map<String, BigDecimal> metrics,
                               Map<String, List<Map<String, Object>>> series) {
    }

    public record ExportTaskVO(String id, String reportType, String status,
                               String downloadUrl, Instant expiresAt,
                               Instant createdAt) {
    }

    public record AuditLogVO(String requestId, String operatorId, String action,
                             String resourceType, String resourceId, String result,
                             String ipAddress, Instant createdAt) {
    }
}
