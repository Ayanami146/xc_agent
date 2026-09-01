package com.xc.agent.common.security;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.config.AuthProperties;
import com.xc.agent.model.enums.AuthEnums;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtService {
    private final AuthProperties properties;
    private final Clock clock;
    private final SecretKey secretKey;

    public JwtService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] secret = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 字节");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret);
    }

    public IssuedToken issue(Long userId, String publicId, AuthEnums.SubjectType subjectType) {
        return issue(userId, publicId, subjectType, null);
    }

    public IssuedToken issue(Long userId, String publicId, AuthEnums.SubjectType subjectType,
                             AuthEnums.AdminRole adminRole) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        String audience = subjectType == AuthEnums.SubjectType.ADMIN ? "admin-web" : "customer-web";
        String token = Jwts.builder()
                .subject(publicId)
                .claim("uid", userId)
                .claim("typ", subjectType.name())
                .claim("role", adminRole == null ? null : adminRole.name())
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token).getPayload();
            Number userId = claims.get("uid", Number.class);
            String subjectType = claims.get("typ", String.class);
            String role = claims.get("role", String.class);
            String publicId = claims.getSubject();
            if (userId == null || subjectType == null || publicId == null) {
                throw invalidToken();
            }
            AuthEnums.SubjectType parsedType = AuthEnums.SubjectType.valueOf(subjectType);
            String expectedAudience = parsedType == AuthEnums.SubjectType.ADMIN ? "admin-web" : "customer-web";
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(expectedAudience)) {
                throw invalidToken();
            }
            AuthEnums.AdminRole parsedRole = role == null ? null : AuthEnums.AdminRole.valueOf(role);
            if (parsedType == AuthEnums.SubjectType.ADMIN && parsedRole == null) {
                throw invalidToken();
            }
            return new AuthPrincipal(userId.longValue(), publicId, parsedType, parsedRole);
        } catch (ExpiredJwtException exception) {
            throw new BusinessException("AUTH_TOKEN_EXPIRED", 401, "登录凭证已过期，请刷新后重试");
        } catch (BusinessException exception) {
            throw exception;
        } catch (JwtException | IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private BusinessException invalidToken() {
        return new BusinessException("AUTH_TOKEN_INVALID", 401, "登录凭证无效");
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
