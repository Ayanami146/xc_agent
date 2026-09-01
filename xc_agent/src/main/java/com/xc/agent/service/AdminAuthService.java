package com.xc.agent.service;

import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.service.admin.AdminAuthModels;
import com.xc.agent.service.auth.AuthModels;

public interface AdminAuthService {
    AdminAuthModels.AdminAuthResult login(AdminDTOs.AdminLoginDTO request,
                                          AuthModels.LoginMetadata metadata);
    AdminAuthModels.AdminAuthResult refresh(String refreshToken);
    void logout(String refreshToken);
}
