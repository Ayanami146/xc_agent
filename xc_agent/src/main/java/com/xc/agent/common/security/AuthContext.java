package com.xc.agent.common.security;

import java.util.Optional;

public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthPrincipal principal) {
        CURRENT.set(principal);
    }

    public static Optional<AuthPrincipal> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static AuthPrincipal required() {
        return current().orElseThrow(() -> new IllegalStateException("当前请求不存在认证上下文"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
