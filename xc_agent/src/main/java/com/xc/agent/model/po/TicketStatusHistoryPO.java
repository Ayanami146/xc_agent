package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketStatusHistoryPO {
    private Long id;
    private String publicId;
    private Long ticketId;
    private String fromStatus;
    private String toStatus;
    private String operatorType;
    private Long operatorId;
    private String title;
    private String reason;
    private String requestId;
    private LocalDateTime createdAt;
}
