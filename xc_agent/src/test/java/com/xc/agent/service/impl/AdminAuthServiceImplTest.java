package com.xc.agent.service.impl;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.security.JwtService;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.AdminProperties;
import com.xc.agent.config.AuthProperties;
import com.xc.agent.mapper.AdminUserMapper;
import com.xc.agent.mapper.RefreshTokenMapper;
import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.po.AdminUserPO;
import com.xc.agent.service.auth.AuthModels;
import com.xc.agent.service.auth.LoginAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;
import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuthServiceImplTest {
    private final Instant now = Instant.parse("2026-08-25T12:00:00Z");
    private AdminUserMapper adminMapper;
    private RefreshTokenMapper refreshMapper;
    private LoginAuditService auditService;
    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        adminMapper = mock(AdminUserMapper.class);
        refreshMapper = mock(RefreshTokenMapper.class);
        auditService = mock(LoginAuditService.class);
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        AuthProperties auth = new AuthProperties(Duration.ofHours(2), Duration.ofDays(30),
                Duration.ofMinutes(5), "test-secret-that-is-longer-than-thirty-two-bytes-2026",
                "CUSTOMER", "/api/v1/auth", false);
        AdminProperties admin = new AdminProperties("ADMIN", "/api/v1/admin/auth", 5,
                Duration.ofMinutes(15), Path.of("build/test-manuals"), 20 * 1024 * 1024);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new AdminAuthServiceImpl(adminMapper, refreshMapper, encoder,
                new JwtService(auth, clock), new CryptoService(new java.security.SecureRandom()),
                auditService, auth, admin, clock);
    }

    @Test
    void wrongPasswordIncrementsFailureAndAuditsGenericError() {
        when(adminMapper.selectByUsername("admin")).thenReturn(admin("{noop}right"));
        assertThatThrownBy(() -> service.login(new AdminDTOs.AdminLoginDTO("admin", "wrong"), metadata()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("AUTH_BAD_CREDENTIALS");
        verify(adminMapper).recordLoginFailure(eq(7L), eq(LocalDateTime.ofInstant(now, ZoneOffset.UTC).plusMinutes(15)), eq(5));
        verify(auditService).recordAdmin(eq(metadata()), anyString(), eq(false), eq("AUTH_BAD_CREDENTIALS"));
    }

    @Test
    void successfulLegacyPasswordLoginUpgradesHashAndIssuesAdminSession() {
        when(adminMapper.selectByUsername("admin")).thenReturn(admin("{noop}123456"));
        var result = service.login(new AdminDTOs.AdminLoginDTO("admin", "123456"), metadata());
        assertThat(result.session().role()).isEqualTo(AuthEnums.AdminRole.ADMIN);
        assertThat(result.session().permissions()).contains("AUDIT_READ", "CONTENT_MANAGE");
        assertThat(result.refreshToken()).startsWith("art1.");
        verify(adminMapper).updatePasswordHash(eq(7L), startsWith("{bcrypt}"));
        verify(adminMapper).resetLoginFailures(7L);
        verify(refreshMapper).insert(any());
        verify(auditService).recordAdmin(eq(metadata()), anyString(), eq(true), isNull());
    }

    private AdminUserPO admin(String passwordHash) {
        return AdminUserPO.builder().id(7L).publicId("admin_demo").username("admin")
                .passwordHash(passwordHash).displayName("王工").roleCode("ADMIN").status("ACTIVE")
                .failedCount(0).version(0).build();
    }

    private AuthModels.LoginMetadata metadata() {
        return new AuthModels.LoginMetadata("req_test", "127.0.0.1", "JUnit");
    }
}
