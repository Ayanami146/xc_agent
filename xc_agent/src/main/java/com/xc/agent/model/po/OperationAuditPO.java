package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class OperationAuditPO {
    private Long id;
    private String requestId;
    private String actorType;
    private String actorId;
    private String actionName;
    private String resourceType;
    private String resourceId;
    private String result;
    private String detailJson;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
