package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerUserPO {
    private Long id;
    private String publicId;
    private String account;
    private String phone;
    private String phoneHash;
    private String passwordHash;
    private String nickname;
    private String avatarText;
    private String status;
    private Integer version;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
