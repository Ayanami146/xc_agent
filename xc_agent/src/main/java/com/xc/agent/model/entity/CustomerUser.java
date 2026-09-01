package com.xc.agent.model.entity;

import com.xc.agent.model.enums.AuthEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerUser {
    private Long id;
    private String publicId;
    private String account;
    private String phone;
    private String nickname;
    private String avatarText;
    private AuthEnums.UserStatus status;
    private LocalDateTime lastLoginAt;
}
