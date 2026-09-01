package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LoginAuditPO {
    private Long id;
    private String subjectType;
    private String subjectRef;
    private String loginMode;
    private String result;
    private String reasonCode;
    private String ipAddress;
    private String userAgent;
    private String requestId;
    private LocalDateTime createdAt;
}
