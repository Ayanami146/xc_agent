package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ApiIdempotencyRecordPO {
    private Long id;
    private String scopeName;
    private String callerId;
    private String idempotencyKey;
    private String requestHash;
    private String resourceType;
    private String resourceId;
    private Integer responseStatus;
    private String responseBody;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
