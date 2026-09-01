package com.xc.agent.model.vo.ticket;

import com.xc.agent.model.enums.TicketEnums;

import java.time.Instant;
import java.util.List;

public final class TicketVOs {
    private TicketVOs() {
    }

    public record TicketAttachmentVO(String id, String fileName, long size, String contentType) {
    }

    public record TicketReplyVO(String id, TicketEnums.SenderType sender, String senderName,
                                String content, Instant createdAt) {
    }

    public record TicketTimelineVO(String id, String title, String description,
                                   TicketEnums.Status status, Instant createdAt) {
    }

    public record TicketSummaryVO(String id, String title, String category, String deviceBrand,
                                  String deviceModel, TicketEnums.Status status,
                                  Instant createdAt, Instant updatedAt) {
    }

    public record TicketDetailVO(String id, String title, String category, String deviceBrand,
                                 String deviceModel, String description, String contact,
                                 TicketEnums.Status status, String assignee,
                                 Instant createdAt, Instant updatedAt,
                                 List<TicketAttachmentVO> attachments,
                                 List<TicketReplyVO> replies,
                                 List<TicketTimelineVO> timeline) {
    }
}
