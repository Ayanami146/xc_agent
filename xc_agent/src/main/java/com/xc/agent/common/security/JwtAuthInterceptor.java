package com.xc.agent.common.security;

import com.xc.agent.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.xc.agent.model.enums.AuthEnums;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtService jwtService;

    public JwtAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException("AUTH_TOKEN_MISSING", 401, "请先登录");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException("AUTH_TOKEN_MISSING", 401, "请先登录");
        }
        AuthPrincipal principal = jwtService.parse(token);
        boolean adminPath = request.getRequestURI().startsWith("/api/v1/admin/");
        AuthEnums.SubjectType expected = adminPath
                ? AuthEnums.SubjectType.ADMIN : AuthEnums.SubjectType.CUSTOMER;
        if (principal.subjectType() != expected) {
            throw new BusinessException("AUTH_FORBIDDEN", 403, "当前登录身份无权访问该接口");
        }
        if (handler instanceof HandlerMethod method) {
            AdminRoleRequired required = method.getMethodAnnotation(AdminRoleRequired.class);
            if (required == null) {
                required = method.getBeanType().getAnnotation(AdminRoleRequired.class);
            }
            if (required != null && (principal.adminRole() == null
                    || Arrays.stream(required.value()).noneMatch(role -> role == principal.adminRole()))) {
                throw new BusinessException("AUTH_FORBIDDEN", 403, "当前管理员角色无权执行该操作");
            }
        }
        AuthContext.set(principal);
        request.setAttribute(AuthPrincipal.class.getName(), principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        AuthContext.clear();
    }
}
