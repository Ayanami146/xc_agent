package com.xc.agent.service.admin;

import com.xc.agent.common.security.AuthContext;
import com.xc.agent.common.security.AuthPrincipal;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.mapper.OperationAuditMapper;
import com.xc.agent.model.po.OperationAuditPO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OperationAuditService {
    private final OperationAuditMapper mapper;
    private final Clock clock;

    public OperationAuditService(OperationAuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String action, String resourceType, String resourceId, Map<String, Object> detail) {
        AuthPrincipal principal = AuthContext.required();
        HttpServletRequest request = currentRequest();
        mapper.insert(OperationAuditPO.builder()
                .requestId(request == null ? "req_unknown" : RequestIdFilter.get(request))
                .actorType("ADMIN").actorId(principal.publicId()).actionName(action)
                .resourceType(resourceType).resourceId(resourceId).result("SUCCESS")
                .detailJson(toJson(detail)).ipAddress(request == null ? null : clientIp(request))
                .userAgent(request == null ? null : limit(request.getHeader("User-Agent"), 500))
                .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).build());
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        return limit(value == null || value.isBlank() ? request.getRemoteAddr() : value.split(",", 2)[0].trim(), 64);
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) return "{}";
        return detail.entrySet().stream()
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\""
                        + escape(String.valueOf(entry.getValue())) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
