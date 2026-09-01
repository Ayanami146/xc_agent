package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.security.AdminRoleRequired;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.vo.admin.AdminManagementVOs;
import com.xc.agent.service.admin.AdminManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminManagementService service;

    public AdminController(AdminManagementService service) {
        this.service = service;
    }

    @GetMapping("/dashboard/overview")
    public ApiResponse<AdminManagementVOs.DashboardVO> dashboard(HttpServletRequest request) {
        return response(request, service.dashboard());
    }

    @GetMapping("/admins")
    @AdminRoleRequired
    public ApiResponse<PageVO<AdminManagementVOs.AdminListItemVO>> admins(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return response(request, service.admins(keyword, role, status, page, pageSize));
    }

    @GetMapping("/audits")
    @AdminRoleRequired
    public ApiResponse<PageVO<AdminManagementVOs.AuditVO>> audits(
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return response(request, service.audits(new AdminDTOs.AuditQueryDTO(
                operatorId, resourceType, action, requestId, from, to, page, pageSize)));
    }

    private <T> ApiResponse<T> response(HttpServletRequest request, T data) {
        return new ApiResponse<>(RequestIdFilter.get(request), data);
    }
}
