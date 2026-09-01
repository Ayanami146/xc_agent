package com.xc.agent.model.dto.chat;

import com.xc.agent.model.enums.ChatEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ChatDTOs {
    private ChatDTOs() {
    }

    public record SessionQueryDTO(
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer pageSize,
            @Size(max = 50) String keyword
    ) {
    }

    public record MessageQueryDTO(
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer pageSize
    ) {
    }

    public record SessionRenameDTO(
            @NotBlank @Size(max = 30) String title
    ) {
    }

    public record ChatStreamDTO(
            @Min(1) Long sessionId,
            @NotBlank @Size(max = 8000) String message
    ) {
    }

    public record MessageFeedbackDTO(ChatEnums.Feedback feedback) {
    }
}
