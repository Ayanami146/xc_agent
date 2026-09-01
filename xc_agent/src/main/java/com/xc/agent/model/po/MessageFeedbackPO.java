package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MessageFeedbackPO {
    private Long id;
    private Long messageId;
    private Long userId;
    private String feedback;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
