package com.xc.agent.common.security;

import com.xc.agent.model.enums.AuthEnums;

public record AuthPrincipal(Long userId, String publicId, AuthEnums.SubjectType subjectType,
                            AuthEnums.AdminRole adminRole) {
    public AuthPrincipal(Long userId, String publicId, AuthEnums.SubjectType subjectType) {
        this(userId, publicId, subjectType, null);
    }
}
