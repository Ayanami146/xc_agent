package com.xc.agent.model.entity;

import com.xc.agent.model.enums.ChatEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatSession {
    private Long id;
    private Long userId;
    private String title;
    private String preview;
    private ChatEnums.SessionStatus status;
    private LocalDateTime lastMessageAt;
    private List<ChatMessage> messages;
}
