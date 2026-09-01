package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketAttachmentPO {
    private Long id;
    private String publicId;
    private Long uploaderUserId;
    private Long ticketId;
    private Long replyId;
    private String objectKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String scanStatus;
    private String bindStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
