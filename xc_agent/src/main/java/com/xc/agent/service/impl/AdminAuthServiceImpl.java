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
import com.xc.agent.model.po.RefreshTokenPO;
import com.xc.agent.model.vo.auth.AuthVOs;
import com.xc.agent.service.AdminAuthService;
import com.xc.agent.service.admin.AdminAuthModels;
import com.xc.agent.service.auth.AuthModels;
import com.xc.agent.service.auth.LoginAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthServiceImpl.class);
    private static final String REFRESH_PREFIX = "art1.";

    private final AdminUserMapper adminUserMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CryptoService cryptoService;
    private final LoginAuditService loginAuditService;
    private final AuthProperties authProperties;
    private final AdminProperties adminProperties;
    private final Clock clock;

    public AdminAuthServiceImpl(AdminUserMapper adminUserMapper, RefreshTokenMapper refreshTokenMapper,
                                PasswordEncoder passwordEncoder, JwtService jwtService,
                                CryptoService cryptoService, LoginAuditService loginAuditService,
                                AuthProperties authProperties, AdminProperties adminProperties, Clock clock) {
        this.adminUserMapper = adminUserMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.cryptoService = cryptoService;
        this.loginAuditService = loginAuditService;
        this.authProperties = authProperties;
        this.adminProperties = adminProperties;
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AdminAuthModels.AdminAuthResult login(AdminDTOs.AdminLoginDTO request,
                                                  AuthModels.LoginMetadata metadata) {
        String account = request.account().trim();
        String subjectRef = cryptoService.sha256Hex("admin:" + account);
        try {
            AdminUserPO admin = adminUserMapper.selectByUsername(account);
            if (admin == null) {
                throw new BusinessException("AUTH_BAD_CREDENTIALS", 401, "账号或密码错误");
            }
            ensureAvailable(admin);
            boolean matches;
            try {
                matches = passwordEncoder.matches(request.password(), admin.getPasswordHash());
            } catch (IllegalArgumentException exception) {
                matches = false;
            }
            if (!matches) {
                adminUserMapper.recordLoginFailure(admin.getId(),
                        nowUtc().plus(adminProperties.loginLockDuration()),
                        adminProperties.maxLoginAttempts());
                throw new BusinessException("AUTH_BAD_CREDENTIALS", 401, "账号或密码错误");
            }
            if (passwordEncoder.upgradeEncoding(admin.getPasswordHash())) {
                adminUserMapper.updatePasswordHash(admin.getId(), passwordEncoder.encode(request.password()));
            }
            adminUserMapper.resetLoginFailures(admin.getId());
            AdminAuthModels.AdminAuthResult result = issueSession(admin, null, null);
            safeAudit(metadata, subjectRef, true, null);
            return result;
        } catch (BusinessException exception) {
            safeAudit(metadata, subjectRef, false, exception.getCode());
            throw exception;
        }
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public AdminAuthModels.AdminAuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank() || !refreshToken.startsWith(REFRESH_PREFIX)) {
            throw invalidRefresh();
        }
        String oldHash = cryptoService.sha256Hex(refreshToken);
        RefreshTokenPO stored = refreshTokenMapper.selectByTokenHashForUpdate(oldHash);
        if (stored == null || stored.getRevokedAt() != null
                || !AuthEnums.SubjectType.ADMIN.name().equals(stored.getSubjectType())) {
            throw invalidRefresh();
        }
        LocalDateTime now = nowUtc();
        if (!stored.getExpiresAt().isAfter(now)) {
            refreshTokenMapper.revoke(oldHash, now);
            throw invalidRefresh();
        }
        AdminUserPO admin = adminUserMapper.selectById(stored.getSubjectId());
        if (admin == null) {
            refreshTokenMapper.revoke(oldHash, now);
            throw invalidRefresh();
        }
        ensureAvailable(admin);
        return issueSession(admin, stored, oldHash);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && refreshToken.startsWith(REFRESH_PREFIX)) {
            refreshTokenMapper.revoke(cryptoService.sha256Hex(refreshToken), nowUtc());
        }
    }

    private AdminAuthModels.AdminAuthResult issueSession(AdminUserPO admin,
                                                          RefreshTokenPO previous, String oldHash) {
        AuthEnums.AdminRole role = AuthEnums.AdminRole.valueOf(admin.getRoleCode());
        JwtService.IssuedToken access = jwtService.issue(
                admin.getId(), admin.getPublicId(), AuthEnums.SubjectType.ADMIN, role);
        String rawRefresh = REFRESH_PREFIX + cryptoService.randomUrlToken(32);
        String newHash = cryptoService.sha256Hex(rawRefresh);
        LocalDateTime now = nowUtc();
        RefreshTokenPO token = RefreshTokenPO.builder()
                .tokenHash(newHash)
                .tokenFamilyId(previous == null ? newId("afam_") : previous.getTokenFamilyId())
                .subjectType(AuthEnums.SubjectType.ADMIN.name())
                .subjectId(admin.getId())
                .deviceId(previous == null ? newId("adev_") : previous.getDeviceId())
                .expiresAt(LocalDateTime.ofInstant(
                        clock.instant().plus(authProperties.refreshTokenTtl()), ZoneOffset.UTC))
                .createdAt(now)
                .build();
        refreshTokenMapper.insert(token);
        if (previous != null && refreshTokenMapper.rotate(oldHash, now, newHash) != 1) {
            throw new BusinessException("AUTH_REFRESH_REPLAYED", 401, "刷新凭证已被重复使用");
        }
        AuthVOs.AdminAuthSessionVO session = new AuthVOs.AdminAuthSessionVO(
                access.value(), access.expiresAt(), admin.getPublicId(),
                admin.getDisplayName(), role, permissions(role));
        return new AdminAuthModels.AdminAuthResult(session, rawRefresh);
    }

    private void ensureAvailable(AdminUserPO admin) {
        if (AuthEnums.UserStatus.DISABLED.name().equals(admin.getStatus())) {
            throw new BusinessException("AUTH_ADMIN_DISABLED", 403, "管理员账号已停用");
        }
        if (AuthEnums.UserStatus.LOCKED.name().equals(admin.getStatus())) {
            throw new BusinessException("AUTH_ADMIN_LOCKED", 403, "管理员账号已锁定");
        }
        if (admin.getLockedUntil() != null && admin.getLockedUntil().isAfter(nowUtc())) {
            throw new BusinessException("AUTH_ADMIN_TEMP_LOCKED", 403, "密码错误次数过多，请稍后重试");
        }
    }

    private List<String> permissions(AuthEnums.AdminRole role) {
        return role == AuthEnums.AdminRole.ADMIN
                ? List.of("DASHBOARD_READ", "TICKET_MANAGE", "CONTENT_MANAGE", "ADMIN_READ", "AUDIT_READ")
                : List.of("DASHBOARD_READ", "TICKET_MANAGE", "CONTENT_READ");
    }

    private void safeAudit(AuthModels.LoginMetadata metadata, String subjectRef,
                           boolean success, String reasonCode) {
        try {
            loginAuditService.recordAdmin(metadata, subjectRef, success, reasonCode);
        } catch (RuntimeException exception) {
            log.warn("管理员登录审计写入失败 requestId={}", metadata.requestId(), exception);
        }
    }

    private String newId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private BusinessException invalidRefresh() {
        return new BusinessException("AUTH_REFRESH_INVALID", 401, "管理员刷新凭证不存在或已失效");
    }
}
