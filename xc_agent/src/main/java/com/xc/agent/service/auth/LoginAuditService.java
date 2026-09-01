package com.xc.agent.service.auth;

import com.xc.agent.mapper.LoginAuditMapper;
import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.po.LoginAuditPO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class LoginAuditService {
    private final LoginAuditMapper loginAuditMapper;
    private final Clock clock;

    public LoginAuditService(LoginAuditMapper loginAuditMapper, Clock clock) {
        this.loginAuditMapper = loginAuditMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuthModels.LoginMetadata metadata, String subjectRef,
                       AuthEnums.LoginMode mode, boolean success, String reasonCode) {
        record(metadata, AuthEnums.SubjectType.CUSTOMER, subjectRef, mode.name(), success, reasonCode);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAdmin(AuthModels.LoginMetadata metadata, String subjectRef,
                            boolean success, String reasonCode) {
        record(metadata, AuthEnums.SubjectType.ADMIN, subjectRef, "password", success, reasonCode);
    }

    private void record(AuthModels.LoginMetadata metadata, AuthEnums.SubjectType subjectType,
                        String subjectRef, String mode, boolean success, String reasonCode) {
        loginAuditMapper.insert(LoginAuditPO.builder()
                .subjectType(subjectType.name())
                .subjectRef(limit(subjectRef, 100))
                .loginMode(mode)
                .result(success ? AuthEnums.LoginResult.SUCCESS.name() : AuthEnums.LoginResult.FAILED.name())
                .reasonCode(reasonCode)
                .ipAddress(limit(metadata.ipAddress(), 64))
                .userAgent(limit(metadata.userAgent(), 500))
                .requestId(limit(metadata.requestId(), 64))
                .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .build());
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
