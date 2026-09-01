package com.xc.agent.model.vo.auth;

import java.time.Instant;
import java.util.List;
import com.xc.agent.model.enums.AuthEnums;

public final class AuthVOs {
    private AuthVOs() {
    }

    public record AuthUserVO(String id, String name, String phone, String avatarText) {
    }

    public record AuthSessionVO(String accessToken, Instant expiresAt, AuthUserVO user) {
    }

    public record AdminMfaChallengeVO(String mfaTicket, List<String> methods, Instant expiresAt) {
    }

    public record AdminAuthSessionVO(String accessToken, Instant expiresAt,
                                     String adminId, String displayName,
                                     AuthEnums.AdminRole role, List<String> permissions) {
    }
}
