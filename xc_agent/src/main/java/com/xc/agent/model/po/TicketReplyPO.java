package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketReplyPO {
    private Long id;
    private String publicId;
    private Long ticketId;
    private String senderType;
    private Long customerUserId;
    private Long adminUserId;
    private String senderNameSnapshot;
    private String content;
    private LocalDateTime createdAt;
}
