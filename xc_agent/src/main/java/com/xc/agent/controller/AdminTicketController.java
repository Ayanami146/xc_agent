package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.security.AdminRoleRequired;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.model.enums.TicketEnums;
import com.xc.agent.model.vo.admin.AdminManagementVOs;
import com.xc.agent.service.admin.AdminManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/tickets")
public class AdminTicketController {
    private final AdminManagementService service;

    public AdminTicketController(AdminManagementService service) { this.service = service; }

    @GetMapping
    public ApiResponse<PageVO<AdminManagementVOs.TicketSummaryVO>> list(
            @RequestParam(required=false) String keyword,
            @RequestParam(required=false) TicketEnums.Status status,
            @RequestParam(required=false) String assigneeId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize,
            HttpServletRequest request) {
        return response(request, service.tickets(keyword,status,assigneeId,from,to,page,pageSize));
    }

    @GetMapping("/assignees")
    public ApiResponse<List<AdminManagementVOs.AdminListItemVO>> assignees(HttpServletRequest request) {
        return response(request, service.activeAssignees());
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminManagementVOs.TicketDetailVO> detail(@PathVariable String id,
                                                                  HttpServletRequest request) {
        return response(request, service.ticket(id));
    }

    @PostMapping("/{id}/assign")
    public ApiResponse<AdminManagementVOs.TicketDetailVO> assign(@PathVariable String id,
            @Valid @RequestBody AdminDTOs.TicketAssignDTO body,
            @RequestHeader("If-Match") String version, HttpServletRequest request) {
        return response(request, service.assignTicket(id,body,version(version)));
    }

    @PostMapping("/{id}/replies")
    public ApiResponse<AdminManagementVOs.TicketDetailVO> reply(@PathVariable String id,
            @Valid @RequestBody AdminDTOs.AdminTicketReplyDTO body, HttpServletRequest request) {
        return response(request, service.replyTicket(id,body));
    }

    @PostMapping("/{id}/transitions")
    public ApiResponse<AdminManagementVOs.TicketDetailVO> transition(@PathVariable String id,
            @Valid @RequestBody AdminDTOs.TicketTransitionDTO body,
            @RequestHeader("If-Match") String version, HttpServletRequest request) {
        return response(request, service.transitionTicket(id,body,version(version)));
    }

    @PostMapping("/{id}/convert-to-faq")
    @AdminRoleRequired
    public ApiResponse<AdminManagementVOs.FaqVO> convert(@PathVariable String id,
            @Valid @RequestBody AdminDTOs.ConvertToFaqDTO body, HttpServletRequest request) {
        return response(request, service.convertTicketToFaq(id,body));
    }

    private int version(String value) {
        try { return Integer.parseInt(value.replace("\"","").trim()); }
        catch (RuntimeException exception) { throw new com.xc.agent.common.exception.BusinessException("IF_MATCH_INVALID",400,"If-Match 必须是资源版本号"); }
    }
    private <T> ApiResponse<T> response(HttpServletRequest request,T data) {
        return new ApiResponse<>(RequestIdFilter.get(request),data);
    }
}
