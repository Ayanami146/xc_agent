package com.xc.agent.common.security;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.config.AuthProperties;
import com.xc.agent.model.enums.AuthEnums;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final String SECRET = "test-secret-that-is-longer-than-thirty-two-bytes-2026";
    private static final Instant NOW = Instant.parse("2026-08-25T12:00:00Z");

    @Test
    void issuesAndParsesAccessToken() {
        JwtService service = serviceAt(NOW);

        JwtService.IssuedToken issued = service.issue(42L, "usr_demo", AuthEnums.SubjectType.CUSTOMER);
        AuthPrincipal principal = service.parse(issued.value());

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.publicId()).isEqualTo("usr_demo");
        assertThat(principal.subjectType()).isEqualTo(AuthEnums.SubjectType.CUSTOMER);
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(2)));
    }

    @Test
    void rejectsExpiredToken() {
        String token = serviceAt(NOW).issue(42L, "usr_demo", AuthEnums.SubjectType.CUSTOMER).value();

        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofHours(3))).parse(token))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("AUTH_TOKEN_EXPIRED");
    }

    @Test
    void adminTokenCarriesRoleAndAdminAudience() {
        JwtService service = serviceAt(NOW);
        String token = service.issue(7L, "admin_demo", AuthEnums.SubjectType.ADMIN,
                AuthEnums.AdminRole.ADMIN).value();
        AuthPrincipal principal = service.parse(token);
        assertThat(principal.subjectType()).isEqualTo(AuthEnums.SubjectType.ADMIN);
        assertThat(principal.adminRole()).isEqualTo(AuthEnums.AdminRole.ADMIN);
    }

    @Test
    void rejectsShortSecretAtStartup() {
        AuthProperties properties = properties("short");

        assertThatThrownBy(() -> new JwtService(properties, Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    private JwtService serviceAt(Instant instant) {
        return new JwtService(properties(SECRET), Clock.fixed(instant, ZoneOffset.UTC));
    }

    private AuthProperties properties(String secret) {
        return new AuthProperties(Duration.ofHours(2), Duration.ofDays(30), Duration.ofMinutes(5),
                secret, "XC_REFRESH_TOKEN", "/api/v1/auth", false);
    }
}
