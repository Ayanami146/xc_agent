package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthSmsCodePO {
    private Long id;
    private String phoneHash;
    private String codeHash;
    private String purpose;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private Integer failedCount;
    private String requestId;
    private LocalDateTime createdAt;
}
