package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatRequestPO {
    private Long id;
    private Long sessionId;
    private Long userId;
    private String requestHash;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String errorMessage;
    private String modelRoute;
    private String usageJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
