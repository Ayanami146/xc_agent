package com.xc.agent.model.entity;

import com.xc.agent.model.enums.ChatEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private Long requestId;
    private ChatEnums.MessageRole role;
    private ChatEnums.MessageStatus status;
    private String content;
    private ChatEnums.Stage stage;
    private LocalDateTime createdAt;
}
