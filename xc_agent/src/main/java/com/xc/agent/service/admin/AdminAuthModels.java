package com.xc.agent.service.admin;

import com.xc.agent.model.vo.auth.AuthVOs;

public final class AdminAuthModels {
    private AdminAuthModels() {
    }

    public record AdminAuthResult(AuthVOs.AdminAuthSessionVO session, String refreshToken) {
    }
}
