package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshTokenPO {
    private Long id;
    private String tokenHash;
    private String tokenFamilyId;
    private String subjectType;
    private Long subjectId;
    private String deviceId;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private String replacedByTokenHash;
    private LocalDateTime createdAt;
}
