package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdminUserPO {
    private Long id;
    private String publicId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String roleCode;
    private String status;
    private Integer failedCount;
    private LocalDateTime lockedUntil;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
