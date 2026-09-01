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
import com.xc.agent.model.vo.auth.AuthVOs;
import com.xc.agent.service.AuthService;
import com.xc.agent.service.auth.AuthModels;
import com.xc.agent.service.auth.LoginAuditService;
import com.xc.agent.service.auth.SmsCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String PERSISTENT_PREFIX = "rt1.p.";
    private static final String SESSION_PREFIX = "rt1.s.";

    private final CustomerUserMapper customerUserMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final SmsCodeService smsCodeService;
    private final LoginAuditService loginAuditService;
    private final JwtService jwtService;
    private final CryptoService cryptoService;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthServiceImpl(CustomerUserMapper customerUserMapper,
                           RefreshTokenMapper refreshTokenMapper,
                           PasswordEncoder passwordEncoder,
                           SmsCodeService smsCodeService,
                           LoginAuditService loginAuditService,
                           JwtService jwtService,
                           CryptoService cryptoService,
                           AuthProperties properties,
                           Clock clock) {
        this.customerUserMapper = customerUserMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.smsCodeService = smsCodeService;
        this.loginAuditService = loginAuditService;
        this.jwtService = jwtService;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void sendSmsCode(AuthDTOs.SendSmsCodeDTO request) {
        smsCodeService.send(request.phone().trim());
    }

    @Override
    @Transactional
    public AuthModels.AuthResult login(AuthDTOs.LoginDTO request, AuthModels.LoginMetadata metadata) {
        AuthEnums.LoginMode mode = request instanceof AuthDTOs.PasswordLoginDTO
                ? AuthEnums.LoginMode.password : AuthEnums.LoginMode.sms;
        String subjectRef = auditSubject(request);
        try {
            CustomerUserPO user = switch (request) {
                case AuthDTOs.PasswordLoginDTO password -> passwordLogin(password);
                case AuthDTOs.SmsLoginDTO sms -> smsLogin(sms);
            };
            ensureActive(user);
            AuthModels.AuthResult result = issueSession(user, request.rememberDevice(), null, null);
            safeAudit(metadata, subjectRef, mode, true, null);
            return result;
        } catch (BusinessException exception) {
            safeAudit(metadata, subjectRef, mode, false, exception.getCode());
            throw exception;
        }
    }

    @Override
    @Transactional
    public AuthModels.AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("AUTH_REFRESH_INVALID", 401, "刷新凭证不存在或已失效");
        }
        String oldHash = cryptoService.sha256Hex(refreshToken);
        RefreshTokenPO stored = refreshTokenMapper.selectByTokenHashForUpdate(oldHash);
        if (stored == null) {
            throw new BusinessException("AUTH_REFRESH_INVALID", 401, "刷新凭证不存在或已失效");
        }
        if (stored.getRevokedAt() != null) {
            String code = stored.getReplacedByTokenHash() == null
                    ? "AUTH_REFRESH_INVALID" : "AUTH_REFRESH_REPLAYED";
            throw new BusinessException(code, 401, "刷新凭证已被使用或撤销");
        }
        LocalDateTime now = nowUtc();
        if (!stored.getExpiresAt().isAfter(now)) {
            refreshTokenMapper.revoke(oldHash, now);
            throw new BusinessException("AUTH_REFRESH_INVALID", 401, "刷新凭证已过期");
        }
        if (!AuthEnums.SubjectType.CUSTOMER.name().equals(stored.getSubjectType())) {
            throw new BusinessException("AUTH_REFRESH_INVALID", 401, "刷新凭证主体无效");
        }
        CustomerUserPO user = customerUserMapper.selectById(stored.getSubjectId());
        if (user == null) {
            refreshTokenMapper.revoke(oldHash, now);
            throw new BusinessException("AUTH_REFRESH_INVALID", 401, "用户登录状态已失效");
        }
        ensureActive(user);
        boolean rememberDevice = refreshToken.startsWith(PERSISTENT_PREFIX);
        return issueSession(user, rememberDevice, stored, oldHash);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenMapper.revoke(cryptoService.sha256Hex(refreshToken), nowUtc());
    }

    private CustomerUserPO passwordLogin(AuthDTOs.PasswordLoginDTO request) {
        String account = request.account().trim();
        CustomerUserPO user = customerUserMapper.selectByAccount(account);
        if (user == null) {
            throw new BusinessException("AUTH_USER_NOT_FOUND", 404, "用户不存在");
        }
        boolean matches;
        try {
            matches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        } catch (IllegalArgumentException exception) {
            matches = false;
        }
        if (!matches) {
            throw new BusinessException("AUTH_BAD_CREDENTIALS", 401, "账号或密码错误");
        }
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            String upgraded = passwordEncoder.encode(request.password());
            customerUserMapper.updatePasswordHash(user.getId(), upgraded);
            user.setPasswordHash(upgraded);
        }
        return user;
    }

    private CustomerUserPO smsLogin(AuthDTOs.SmsLoginDTO request) {
        String phone = request.phone().trim();
        smsCodeService.verifyAndConsume(phone, request.code());
        String phoneHash = cryptoService.sha256Hex(phone);
        CustomerUserPO user = customerUserMapper.selectByPhoneHash(phoneHash);
        if (user != null) {
            return user;
        }
        try {
            user = newPhoneUser(phone, phoneHash);
            customerUserMapper.insert(user);
            return user;
        } catch (DataIntegrityViolationException exception) {
            CustomerUserPO existing = customerUserMapper.selectByPhoneHash(phoneHash);
            if (existing != null) {
                return existing;
            }
            throw exception;
        }
    }

    private CustomerUserPO newPhoneUser(String phone, String phoneHash) {
        String idPart = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = nowUtc();
        return CustomerUserPO.builder()
                .publicId("usr_" + idPart)
                .account("u_" + idPart)
                .phone(phone)
                .phoneHash(phoneHash)
                .passwordHash(passwordEncoder.encode(cryptoService.randomUrlToken(32)))
                .nickname("用户" + phone.substring(phone.length() - 4))
                .avatarText("用")
                .status(AuthEnums.UserStatus.ACTIVE.name())
                .version(0)
                .lastLoginAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private AuthModels.AuthResult issueSession(CustomerUserPO user, boolean rememberDevice,
                                                RefreshTokenPO previous, String previousHash) {
        LocalDateTime now = nowUtc();
        customerUserMapper.updateLastLoginAt(user.getId(), now);

        JwtService.IssuedToken accessToken = jwtService.issue(
                user.getId(), user.getPublicId(), AuthEnums.SubjectType.CUSTOMER);
        String rawRefreshToken = newRefreshToken(rememberDevice);
        String newHash = cryptoService.sha256Hex(rawRefreshToken);
        String familyId = previous == null
                ? "fam_" + UUID.randomUUID().toString().replace("-", "")
                : previous.getTokenFamilyId();
        String deviceId = previous == null
                ? "dev_" + UUID.randomUUID().toString().replace("-", "")
                : previous.getDeviceId();

        RefreshTokenPO token = RefreshTokenPO.builder()
                .tokenHash(newHash)
                .tokenFamilyId(familyId)
                .subjectType(AuthEnums.SubjectType.CUSTOMER.name())
                .subjectId(user.getId())
                .deviceId(deviceId)
                .expiresAt(LocalDateTime.ofInstant(clock.instant().plus(properties.refreshTokenTtl()), ZoneOffset.UTC))
                .createdAt(now)
                .build();
        refreshTokenMapper.insert(token);
        if (previous != null && refreshTokenMapper.rotate(previousHash, now, newHash) != 1) {
            throw new BusinessException("AUTH_REFRESH_REPLAYED", 401, "刷新凭证已被重复使用");
        }

        AuthVOs.AuthUserVO authUser = new AuthVOs.AuthUserVO(
                user.getPublicId(), user.getNickname(), maskPhone(user.getPhone()), user.getAvatarText());
        AuthVOs.AuthSessionVO session = new AuthVOs.AuthSessionVO(
                accessToken.value(), accessToken.expiresAt(), authUser);
        return new AuthModels.AuthResult(session, rawRefreshToken, rememberDevice);
    }

    private void ensureActive(CustomerUserPO user) {
        if (!AuthEnums.UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new BusinessException("AUTH_USER_DISABLED", 403,
                    AuthEnums.UserStatus.LOCKED.name().equals(user.getStatus())
                            ? "用户已被锁定" : "用户已被停用");
        }
    }

    private String auditSubject(AuthDTOs.LoginDTO request) {
        String value = switch (request) {
            case AuthDTOs.PasswordLoginDTO password -> "account:" + password.account().trim();
            case AuthDTOs.SmsLoginDTO sms -> "phone:" + sms.phone().trim();
        };
        return cryptoService.sha256Hex(value);
    }

    private void safeAudit(AuthModels.LoginMetadata metadata, String subjectRef,
                           AuthEnums.LoginMode mode, boolean success, String reasonCode) {
        try {
            loginAuditService.record(metadata, subjectRef, mode, success, reasonCode);
        } catch (RuntimeException exception) {
            log.warn("登录审计写入失败 requestId={}", metadata.requestId(), exception);
        }
    }

    private String newRefreshToken(boolean rememberDevice) {
        return (rememberDevice ? PERSISTENT_PREFIX : SESSION_PREFIX) + cryptoService.randomUrlToken(32);
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
