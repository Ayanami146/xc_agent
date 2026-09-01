package com.xc.agent.model.vo.admin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AdminManagementVOs {
    private AdminManagementVOs() {
    }

    public record DashboardVO(Map<String, Long> metrics, List<Map<String, Object>> ticketStatus,
                              List<Map<String, Object>> ticketTrend) {
    }

    public record AdminListItemVO(String id, String account, String name, String role,
                                  String status, int failedCount, Instant lockedUntil,
                                  int version, Instant updatedAt) {
    }

    public record TicketSummaryVO(String id, String title, String category, String customerName,
                                  String status, String assigneeId, String assigneeName,
                                  int version, Instant createdAt, Instant updatedAt) {
    }

    public record TicketReplyVO(String id, String senderType, String senderName,
                                String content, Instant createdAt) {
    }

    public record TicketAttachmentVO(String id, String fileName, String contentType,
                                     long fileSize, String scanStatus) {
    }

    public record TicketHistoryVO(String id, String fromStatus, String toStatus,
                                  String title, String reason, Instant createdAt) {
    }

    public record TicketDetailVO(String id, String title, String category,
                                 String deviceBrand, String deviceModel, String description,
                                 String contact, String customerName, String status,
                                 String assigneeId, String assigneeName, String resolution,
                                 int version, Instant createdAt, Instant updatedAt,
                                 List<TicketReplyVO> replies,
                                 List<TicketAttachmentVO> attachments,
                                 List<TicketHistoryVO> timeline) {
    }

    public record CategoryVO(String id, String name, int sortOrder, String status,
                             int version, Instant updatedAt) {
    }

    public record FaqVO(String id, String categoryId, String title, String question,
                        String answer, String summary, String keywords, String status,
                        boolean top, int hotCount, int version,
                        Instant publishedAt, Instant updatedAt) {
    }

    public record ManualVO(String id, String categoryId, String title, String summary,
                           String fileName, String contentType, long fileSize,
                           String scanStatus, String status, int versionNo, int version,
                           Instant publishedAt, Instant updatedAt) {
    }

    public record AuditVO(String requestId, String actorId, String action,
                          String resourceType, String resourceId, String result,
                          String detailJson, String ipAddress, Instant createdAt) {
    }
}
