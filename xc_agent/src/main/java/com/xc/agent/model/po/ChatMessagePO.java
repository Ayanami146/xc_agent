package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMessagePO {
    private Long id;
    private Long sessionId;
    private Long requestId;
    private String role;
    private String status;
    private String content;
    private String stage;
    private String intentType;
    private String modelName;
    private Integer tokenCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
