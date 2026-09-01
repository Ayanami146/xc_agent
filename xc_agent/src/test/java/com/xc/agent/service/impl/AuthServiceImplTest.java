package com.xc.agent.service.impl;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.security.JwtService;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.AuthProperties;
import com.xc.agent.mapper.CustomerUserMapper;
import com.xc.agent.mapper.RefreshTokenMapper;
import com.xc.agent.model.dto.auth.AuthDTOs;
import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.po.CustomerUserPO;
import com.xc.agent.model.po.RefreshTokenPO;
import com.xc.agent.service.auth.AuthModels;
import com.xc.agent.service.auth.LoginAuditService;
import com.xc.agent.service.auth.SmsCodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");
    private CustomerUserMapper customerUserMapper;
    private RefreshTokenMapper refreshTokenMapper;
    private SmsCodeService smsCodeService;
    private LoginAuditService loginAuditService;
    private PasswordEncoder passwordEncoder;
    private CryptoService cryptoService;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        customerUserMapper = mock(CustomerUserMapper.class);
        refreshTokenMapper = mock(RefreshTokenMapper.class);
        smsCodeService = mock(SmsCodeService.class);
        loginAuditService = mock(LoginAuditService.class);
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.setSeed(2026L);
        cryptoService = new CryptoService(secureRandom);
        AuthProperties properties = new AuthProperties(Duration.ofHours(2), Duration.ofDays(30),
                Duration.ofMinutes(5), "test-secret-that-is-longer-than-thirty-two-bytes-2026",
                "XC_REFRESH_TOKEN", "/api/v1/auth", false);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AuthServiceImpl(customerUserMapper, refreshTokenMapper, passwordEncoder,
                smsCodeService, loginAuditService, new JwtService(properties, clock), cryptoService,
                properties, clock);
    }

    @Test
    void reportsMissingPasswordUserAndAuditsFailure() {
        AuthDTOs.PasswordLoginDTO request = new AuthDTOs.PasswordLoginDTO(
                AuthEnums.LoginMode.password, "missing", "123456", false);
        AuthModels.LoginMetadata metadata = metadata();

        assertThatThrownBy(() -> service.login(request, metadata))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("AUTH_USER_NOT_FOUND");
        verify(loginAuditService).record(any(), anyString(),
                org.mockito.ArgumentMatchers.eq(AuthEnums.LoginMode.password),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq("AUTH_USER_NOT_FOUND"));
    }

    @Test
    void upgradesLegacyPasswordAndIssuesPersistentSession() {
        CustomerUserPO user = activeUser();
        user.setPasswordHash("{noop}123456");
        when(customerUserMapper.selectByAccount("demo")).thenReturn(user);

        AuthModels.AuthResult result = service.login(new AuthDTOs.PasswordLoginDTO(
                AuthEnums.LoginMode.password, "demo", "123456", true), metadata());

        assertThat(result.refreshToken()).startsWith("rt1.p.");
        assertThat(result.session().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
        assertThat(result.session().user().phone()).isEqualTo("138****8000");
        verify(customerUserMapper).updatePasswordHash(
                org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.argThat(hash -> hash.startsWith("{bcrypt}")));
        verify(refreshTokenMapper).insert(any(RefreshTokenPO.class));
    }

    @Test
    void createsUserForFirstSuccessfulSmsLogin() {
        when(customerUserMapper.selectByPhoneHash(anyString())).thenReturn(null);
        doAnswer(invocation -> {
            CustomerUserPO user = invocation.getArgument(0);
            user.setId(99L);
            return 1;
        }).when(customerUserMapper).insert(any(CustomerUserPO.class));

        AuthModels.AuthResult result = service.login(new AuthDTOs.SmsLoginDTO(
                AuthEnums.LoginMode.sms, "13800138000", "123456", false), metadata());

        verify(smsCodeService).verifyAndConsume("13800138000", "123456");
        ArgumentCaptor<CustomerUserPO> userCaptor = ArgumentCaptor.forClass(CustomerUserPO.class);
        verify(customerUserMapper).insert(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccount()).startsWith("u_");
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("用户8000");
        assertThat(result.refreshToken()).startsWith("rt1.s.");
    }

    @Test
    void rotatesRefreshTokenAndPreservesRememberDevice() {
        String oldToken = "rt1.p.old-refresh-token";
        RefreshTokenPO stored = RefreshTokenPO.builder()
                .tokenHash(cryptoService.sha256Hex(oldToken))
                .tokenFamilyId("fam_demo")
                .subjectType(AuthEnums.SubjectType.CUSTOMER.name())
                .subjectId(7L)
                .deviceId("dev_demo")
                .expiresAt(LocalDateTime.ofInstant(NOW.plus(Duration.ofDays(1)), ZoneOffset.UTC))
                .createdAt(LocalDateTime.ofInstant(NOW.minus(Duration.ofDays(1)), ZoneOffset.UTC))
                .build();
        when(refreshTokenMapper.selectByTokenHashForUpdate(stored.getTokenHash())).thenReturn(stored);
        when(customerUserMapper.selectById(7L)).thenReturn(activeUser());
        when(refreshTokenMapper.rotate(anyString(), any(), anyString())).thenReturn(1);

        AuthModels.AuthResult result = service.refresh(oldToken);

        assertThat(result.refreshToken()).startsWith("rt1.p.");
        verify(refreshTokenMapper).rotate(
                org.mockito.ArgumentMatchers.eq(stored.getTokenHash()), any(), anyString());
    }

    private CustomerUserPO activeUser() {
        return CustomerUserPO.builder()
                .id(7L)
                .publicId("usr_demo")
                .account("demo")
                .phone("13800138000")
                .passwordHash("{noop}123456")
                .nickname("演示用户")
                .avatarText("演")
                .status(AuthEnums.UserStatus.ACTIVE.name())
                .version(0)
                .build();
    }

    private AuthModels.LoginMetadata metadata() {
        return new AuthModels.LoginMetadata("req_test", "127.0.0.1", "JUnit");
    }
}
