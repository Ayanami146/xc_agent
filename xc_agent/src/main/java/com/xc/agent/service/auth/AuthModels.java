package com.xc.agent.service.auth;

import com.xc.agent.model.vo.auth.AuthVOs;

public final class AuthModels {
    private AuthModels() {
    }

    public record LoginMetadata(String requestId, String ipAddress, String userAgent) {
    }

    public record AuthResult(AuthVOs.AuthSessionVO session, String refreshToken,
                             boolean rememberDevice) {
    }
}
