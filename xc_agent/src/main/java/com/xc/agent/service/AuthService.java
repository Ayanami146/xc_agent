package com.xc.agent.service;

import com.xc.agent.model.dto.auth.AuthDTOs;
import com.xc.agent.service.auth.AuthModels;

public interface AuthService {
    void sendSmsCode(AuthDTOs.SendSmsCodeDTO request);

    AuthModels.AuthResult login(AuthDTOs.LoginDTO request, AuthModels.LoginMetadata metadata);

    AuthModels.AuthResult refresh(String refreshToken);

    void logout(String refreshToken);
}
