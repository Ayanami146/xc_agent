package com.xc.agent.common.security;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.model.enums.AuthEnums;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthInterceptorTest {
    @Test
    void rejectsCustomerTokenOnAdminPath() {
        JwtService jwt = mock(JwtService.class);
        when(jwt.parse("token")).thenReturn(new AuthPrincipal(1L, "user", AuthEnums.SubjectType.CUSTOMER));
        JwtAuthInterceptor interceptor = new JwtAuthInterceptor(jwt);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/dashboard/overview");
        request.addHeader("Authorization", "Bearer token");
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("AUTH_FORBIDDEN");
    }
}
