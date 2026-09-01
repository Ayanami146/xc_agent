package com.xc.agent.model.vo.chat;

import com.xc.agent.model.enums.ChatEnums;

import java.time.Instant;

public record SseEventVO<T>(
        ChatEnums.StreamEvent event,
        Long requestId,
        long sequence,
        Instant occurredAt,
        T payload
) {
}
