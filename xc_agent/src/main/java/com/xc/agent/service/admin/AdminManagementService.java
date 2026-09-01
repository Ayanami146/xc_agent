package com.xc.agent.service.admin;

import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.security.AuthContext;
import com.xc.agent.common.security.AuthPrincipal;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.mapper.*;
import com.xc.agent.model.dto.admin.AdminDTOs;
import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.enums.TicketEnums;
import com.xc.agent.model.po.*;
import com.xc.agent.model.vo.admin.AdminManagementVOs;
import com.xc.agent.service.content.FaqCacheService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminManagementService {
    private final AdminDashboardMapper dashboardMapper;
    private final AdminUserMapper adminUserMapper;
    private final CustomerUserMapper customerUserMapper;
    private final TicketMapper ticketMapper;
    private final TicketReplyMapper replyMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final TicketStatusHistoryMapper historyMapper;
    private final FaqCategoryMapper faqCategoryMapper;
    private final FaqItemMapper faqItemMapper;
    private final ManualCategoryMapper manualCategoryMapper;
    private final ManualDocMapper manualDocMapper;
    private final OperationAuditMapper operationAuditMapper;
    private final OperationAuditService auditService;
    private final ManualStorageService storageService;
    private final FaqCacheService faqCacheService;
    private final Clock clock;

    public AdminManagementService(AdminDashboardMapper dashboardMapper, AdminUserMapper adminUserMapper,
                                  CustomerUserMapper customerUserMapper, TicketMapper ticketMapper,
                                  TicketReplyMapper replyMapper, TicketAttachmentMapper attachmentMapper,
                                  TicketStatusHistoryMapper historyMapper, FaqCategoryMapper faqCategoryMapper,
                                  FaqItemMapper faqItemMapper, ManualCategoryMapper manualCategoryMapper,
                                  ManualDocMapper manualDocMapper, OperationAuditMapper operationAuditMapper,
                                  OperationAuditService auditService, ManualStorageService storageService,
                                  FaqCacheService faqCacheService, Clock clock) {
        this.dashboardMapper = dashboardMapper;
        this.adminUserMapper = adminUserMapper;
        this.customerUserMapper = customerUserMapper;
        this.ticketMapper = ticketMapper;
        this.replyMapper = replyMapper;
        this.attachmentMapper = attachmentMapper;
        this.historyMapper = historyMapper;
        this.faqCategoryMapper = faqCategoryMapper;
        this.faqItemMapper = faqItemMapper;
        this.manualCategoryMapper = manualCategoryMapper;
        this.manualDocMapper = manualDocMapper;
        this.operationAuditMapper = operationAuditMapper;
        this.auditService = auditService;
        this.storageService = storageService;
        this.faqCacheService = faqCacheService;
        this.clock = clock;
    }

    public AdminManagementVOs.DashboardVO dashboard() {
        return new AdminManagementVOs.DashboardVO(
                dashboardMapper.selectMetrics(), dashboardMapper.selectTicketStatus(),
                dashboardMapper.selectTicketTrend());
    }

    public PageVO<AdminManagementVOs.AdminListItemVO> admins(String keyword, String role,
                                                             String status, int page, int pageSize) {
        int p = page(page), size = pageSize(pageSize), offset = (p - 1) * size;
        List<AdminManagementVOs.AdminListItemVO> items = adminUserMapper
                .selectPage(trim(keyword), trim(role), trim(status), offset, size).stream()
                .map(this::adminVO).toList();
        return new PageVO<>(items, adminUserMapper.countPage(trim(keyword), trim(role), trim(status)), p, size);
    }

    public PageVO<AdminManagementVOs.TicketSummaryVO> tickets(
            String keyword, TicketEnums.Status status, String assigneePublicId,
            LocalDate from, LocalDate to, int page, int pageSize) {
        Long assigneeId = null;
        if (assigneePublicId != null && !assigneePublicId.isBlank()) {
            assigneeId = requireAdmin(assigneePublicId).getId();
        }
        int p = page(page), size = pageSize(pageSize), offset = (p - 1) * size;
        List<TicketPO> rows = ticketMapper.selectAdminPage(trim(keyword),
                status == null ? null : status.name(), assigneeId, from, to, offset, size);
        return new PageVO<>(rows.stream().map(this::ticketSummary).toList(),
                ticketMapper.countAdminPage(trim(keyword), status == null ? null : status.name(),
                        assigneeId, from, to), p, size);
    }

    public List<AdminManagementVOs.AdminListItemVO> activeAssignees() {
        return adminUserMapper.selectPage(null, null, "ACTIVE", 0, 100).stream()
                .map(this::adminVO).toList();
    }

    public AdminManagementVOs.TicketDetailVO ticket(String id) {
        return ticketDetail(requireTicket(id));
    }

    @Transactional
    public AdminManagementVOs.TicketDetailVO assignTicket(String id, AdminDTOs.TicketAssignDTO request,
                                                           int expectedVersion) {
        TicketPO ticket = requireTicket(id);
        AdminUserPO assignee = requireAdmin(request.assigneeId());
        if (!AuthEnums.UserStatus.ACTIVE.name().equals(assignee.getStatus())) {
            throw new BusinessException("ADMIN_ASSIGNEE_UNAVAILABLE", 409, "目标客服账号不可用");
        }
        if (ticketMapper.assign(ticket.getId(), assignee.getId(), expectedVersion) != 1) conflict();
        history(ticket, "PROCESSING", "分配客服", "分配给 " + assignee.getDisplayName());
        auditService.success("TICKET_ASSIGN", "TICKET", id,
                Map.of("assigneeId", assignee.getPublicId()));
        return ticket(id);
    }

    @Transactional
    public AdminManagementVOs.TicketDetailVO replyTicket(String id, AdminDTOs.AdminTicketReplyDTO request) {
        TicketPO ticket = requireTicket(id);
        if (!Set.of("PROCESSING", "WAITING_USER").contains(ticket.getStatus())) {
            throw new BusinessException("TICKET_STATE_INVALID", 409, "当前工单状态不能回复");
        }
        if (request.attachmentIds() != null && !request.attachmentIds().isEmpty()) {
            throw new BusinessException("ADMIN_REPLY_ATTACHMENT_UNSUPPORTED", 400, "本阶段管理员回复仅支持文本");
        }
        AuthPrincipal principal = AuthContext.required();
        AdminUserPO admin = adminUserMapper.selectById(principal.userId());
        TicketReplyPO reply = TicketReplyPO.builder().publicId(id("rpl_"))
                .ticketId(ticket.getId()).senderType("admin").adminUserId(admin.getId())
                .senderNameSnapshot(admin.getDisplayName()).content(request.content().trim())
                .createdAt(now()).build();
        replyMapper.insert(reply);
        auditService.success("TICKET_REPLY", "TICKET", id, Map.of("replyId", reply.getPublicId()));
        return ticket(id);
    }

    @Transactional
    public AdminManagementVOs.TicketDetailVO transitionTicket(String id,
                                                               AdminDTOs.TicketTransitionDTO request,
                                                               int expectedVersion) {
        TicketPO ticket = requireTicket(id);
        String target = request.targetStatus().name();
        if (!validTransition(ticket.getStatus(), target)) {
            throw new BusinessException("TICKET_STATE_INVALID", 409,
                    "不允许从 " + ticket.getStatus() + " 流转到 " + target);
        }
        LocalDateTime time = now();
        if (ticketMapper.transition(ticket.getId(), ticket.getStatus(), target,
                target.equals("RESOLVED") ? request.reason() : null,
                target.equals("RESOLVED") ? time : null,
                target.equals("CLOSED") ? time : null, expectedVersion) != 1) conflict();
        history(ticket, target, "工单状态更新", request.reason());
        auditService.success("TICKET_TRANSITION", "TICKET", id,
                Map.of("from", ticket.getStatus(), "to", target));
        return ticket(id);
    }

    @Transactional
    public AdminManagementVOs.FaqVO convertTicketToFaq(String id, AdminDTOs.ConvertToFaqDTO request) {
        TicketPO ticket = requireTicket(id);
        if (!Set.of("RESOLVED", "CLOSED").contains(ticket.getStatus())) {
            throw new BusinessException("TICKET_STATE_INVALID", 409, "只有已解决或已关闭工单可以转为 FAQ");
        }
        AdminDTOs.FaqSaveDTO faq = new AdminDTOs.FaqSaveDTO(request.categoryId(), request.title(),
                request.question(), request.answer(), "由工单 " + id + " 转换", ticket.getCategory(), false);
        AdminManagementVOs.FaqVO result = createFaq(faq);
        auditService.success("TICKET_CONVERT_FAQ", "TICKET", id, Map.of("faqId", result.id()));
        return result;
    }

    public List<AdminManagementVOs.CategoryVO> faqCategories() {
        return faqCategoryMapper.selectAll().stream().map(this::categoryVO).toList();
    }

    @Transactional
    public AdminManagementVOs.CategoryVO createFaqCategory(AdminDTOs.CategorySaveDTO request) {
        LocalDateTime now = now();
        FaqCategoryPO category = FaqCategoryPO.builder().publicId(id("faqcat_"))
                .name(request.name().trim()).sortOrder(defaultInt(request.sortOrder()))
                .status(defaultStatus(request.status())).version(0).createdAt(now).updatedAt(now).build();
        faqCategoryMapper.insert(category);
        auditService.success("FAQ_CATEGORY_CREATE", "FAQ_CATEGORY", category.getPublicId(), Map.of("name", category.getName()));
        faqCacheService.evictAllAfterCommit();
        return categoryVO(category);
    }

    @Transactional
    public AdminManagementVOs.CategoryVO updateFaqCategory(String id, AdminDTOs.CategorySaveDTO request,
                                                            int expectedVersion) {
        FaqCategoryPO category = requireFaqCategory(id);
        category.setName(request.name().trim()); category.setSortOrder(defaultInt(request.sortOrder()));
        category.setStatus(defaultStatus(request.status()));
        if (faqCategoryMapper.update(category, expectedVersion) != 1) conflict();
        auditService.success("FAQ_CATEGORY_UPDATE", "FAQ_CATEGORY", id, Map.of("name", category.getName()));
        faqCacheService.evictAllAfterCommit();
        return categoryVO(requireFaqCategory(id));
    }

    @Transactional
    public void deleteFaqCategory(String id, int expectedVersion) {
        FaqCategoryPO category = requireFaqCategory(id);
        if (faqCategoryMapper.countItems(category.getId()) > 0) inUse();
        if (faqCategoryMapper.delete(category.getId(), expectedVersion) != 1) conflict();
        auditService.success("FAQ_CATEGORY_DELETE", "FAQ_CATEGORY", id, Map.of("name", category.getName()));
        faqCacheService.evictAllAfterCommit();
    }

    @Transactional
    public List<AdminManagementVOs.CategoryVO> reorderFaqCategories(AdminDTOs.CategoryOrderDTO request) {
        for (AdminDTOs.CategoryOrderItemDTO item : request.items()) {
            FaqCategoryPO category = requireFaqCategory(item.id());
            if (faqCategoryMapper.updateOrder(category.getId(), item.sortOrder(), item.version()) != 1) conflict();
        }
        auditService.success("FAQ_CATEGORY_REORDER", "FAQ_CATEGORY", "batch",
                Map.of("count", request.items().size()));
        faqCacheService.evictAllAfterCommit();
        return faqCategories();
    }

    public PageVO<AdminManagementVOs.FaqVO> faqs(String keyword, String categoryId, String status,
                                                  int page, int pageSize) {
        Long category = categoryId == null || categoryId.isBlank() ? null : requireFaqCategory(categoryId).getId();
        int p = page(page), size = pageSize(pageSize);
        List<FaqItemPO> rows = faqItemMapper.selectPage(trim(keyword), category, trim(status), (p - 1) * size, size);
        return new PageVO<>(rows.stream().map(this::faqVO).toList(),
                faqItemMapper.countPage(trim(keyword), category, trim(status)), p, size);
    }

    public AdminManagementVOs.FaqVO faq(String id) { return faqVO(requireFaq(id)); }

    @Transactional
    public AdminManagementVOs.FaqVO createFaq(AdminDTOs.FaqSaveDTO request) {
        LocalDateTime now = now();
        FaqItemPO item = FaqItemPO.builder().publicId(id("faq_"))
                .categoryId(requireFaqCategory(request.categoryId()).getId())
                .title(request.title().trim()).question(request.question().trim()).answer(request.answer().trim())
                .summary(nullToEmpty(request.summary())).keywords(nullToEmpty(request.keywords()))
                .status("DRAFT").isTop(Boolean.TRUE.equals(request.top())).hotCount(0).version(0)
                .createdAt(now).updatedAt(now).build();
        faqItemMapper.insert(item);
        auditService.success("FAQ_CREATE", "FAQ", item.getPublicId(), Map.of("title", item.getTitle()));
        faqCacheService.evictAllAfterCommit();
        return faqVO(item);
    }

    @Transactional
    public AdminManagementVOs.FaqVO updateFaq(String id, AdminDTOs.FaqSaveDTO request,
                                               int expectedVersion) {
        FaqItemPO item = requireFaq(id);
        item.setCategoryId(requireFaqCategory(request.categoryId()).getId()); item.setTitle(request.title().trim());
        item.setQuestion(request.question().trim()); item.setAnswer(request.answer().trim());
        item.setSummary(nullToEmpty(request.summary())); item.setKeywords(nullToEmpty(request.keywords()));
        item.setIsTop(Boolean.TRUE.equals(request.top()));
        if (faqItemMapper.update(item, expectedVersion) != 1) conflict();
        auditService.success("FAQ_UPDATE", "FAQ", id, Map.of("title", item.getTitle()));
        faqCacheService.evictAllAfterCommit();
        return faq(id);
    }

    @Transactional
    public AdminManagementVOs.FaqVO setFaqStatus(String id, String status, int expectedVersion) {
        FaqItemPO item = requireFaq(id);
        if (!Set.of("PUBLISHED", "ARCHIVED").contains(status)) {
            throw new BusinessException("CONTENT_STATUS_INVALID", 400, "FAQ 状态操作无效");
        }
        if (faqItemMapper.updateStatus(item.getId(), status,
                status.equals("PUBLISHED") ? now() : null, expectedVersion) != 1) conflict();
        auditService.success("FAQ_" + status, "FAQ", id, Map.of("status", status));
        faqCacheService.evictAllAfterCommit();
        return faq(id);
    }

    public List<AdminManagementVOs.CategoryVO> manualCategories() {
        return manualCategoryMapper.selectAll().stream().map(this::categoryVO).toList();
    }

    @Transactional
    public AdminManagementVOs.CategoryVO createManualCategory(AdminDTOs.CategorySaveDTO request) {
        LocalDateTime now = now();
        ManualCategoryPO category = ManualCategoryPO.builder().publicId(id("mancat_"))
                .name(request.name().trim()).sortOrder(defaultInt(request.sortOrder()))
                .status(defaultStatus(request.status())).version(0).createdAt(now).updatedAt(now).build();
        manualCategoryMapper.insert(category);
        auditService.success("MANUAL_CATEGORY_CREATE", "MANUAL_CATEGORY", category.getPublicId(), Map.of("name", category.getName()));
        return categoryVO(category);
    }

    @Transactional
    public AdminManagementVOs.CategoryVO updateManualCategory(String id, AdminDTOs.CategorySaveDTO request,
                                                               int expectedVersion) {
        ManualCategoryPO category = requireManualCategory(id);
        category.setName(request.name().trim()); category.setSortOrder(defaultInt(request.sortOrder()));
        category.setStatus(defaultStatus(request.status()));
        if (manualCategoryMapper.update(category, expectedVersion) != 1) conflict();
        auditService.success("MANUAL_CATEGORY_UPDATE", "MANUAL_CATEGORY", id, Map.of("name", category.getName()));
        return categoryVO(requireManualCategory(id));
    }

    @Transactional
    public void deleteManualCategory(String id, int expectedVersion) {
        ManualCategoryPO category = requireManualCategory(id);
        if (manualCategoryMapper.countItems(category.getId()) > 0) inUse();
        if (manualCategoryMapper.delete(category.getId(), expectedVersion) != 1) conflict();
        auditService.success("MANUAL_CATEGORY_DELETE", "MANUAL_CATEGORY", id, Map.of("name", category.getName()));
    }

    public PageVO<AdminManagementVOs.ManualVO> manuals(String keyword, String categoryId, String status,
                                                        int page, int pageSize) {
        Long category = categoryId == null || categoryId.isBlank() ? null : requireManualCategory(categoryId).getId();
        int p = page(page), size = pageSize(pageSize);
        List<ManualDocPO> rows = manualDocMapper.selectPage(trim(keyword), category, trim(status), (p - 1) * size, size);
        return new PageVO<>(rows.stream().map(this::manualVO).toList(),
                manualDocMapper.countPage(trim(keyword), category, trim(status)), p, size);
    }

    public AdminManagementVOs.ManualVO manual(String id) { return manualVO(requireManual(id)); }

    @Transactional
    public AdminManagementVOs.ManualVO createManual(String categoryId, String title, String summary,
                                                     MultipartFile file) {
        ManualCategoryPO category = requireManualCategory(categoryId);
        ManualStorageService.StoredFile stored = storageService.store(file);
        LocalDateTime now = now();
        ManualDocPO doc = ManualDocPO.builder().publicId(id("man_"))
                .categoryId(category.getId()).title(required(title, "手册标题不能为空"))
                .summary(nullToEmpty(summary)).objectKey(stored.objectKey()).fileName(stored.fileName())
                .contentType(stored.contentType()).fileSize(stored.fileSize()).sha256(stored.sha256())
                .scanStatus("PASSED").status("DRAFT").versionNo(1).version(0)
                .createdAt(now).updatedAt(now).build();
        manualDocMapper.insert(doc);
        auditService.success("MANUAL_CREATE", "MANUAL", doc.getPublicId(), Map.of("fileName", stored.fileName()));
        return manualVO(doc);
    }

    @Transactional
    public AdminManagementVOs.ManualVO updateManual(String id, AdminDTOs.ManualSaveDTO request,
                                                     int expectedVersion) {
        ManualDocPO doc = requireManual(id);
        doc.setCategoryId(requireManualCategory(request.categoryId()).getId());
        doc.setTitle(request.title().trim()); doc.setSummary(nullToEmpty(request.summary()));
        if (manualDocMapper.updateMetadata(doc, expectedVersion) != 1) conflict();
        auditService.success("MANUAL_UPDATE", "MANUAL", id, Map.of("title", doc.getTitle()));
        return manual(id);
    }

    @Transactional
    public AdminManagementVOs.ManualVO replaceManualFile(String id, MultipartFile file,
                                                          int expectedVersion) {
        ManualDocPO doc = requireManual(id);
        ManualStorageService.StoredFile stored = storageService.store(file);
        doc.setObjectKey(stored.objectKey()); doc.setFileName(stored.fileName());
        doc.setContentType(stored.contentType()); doc.setFileSize(stored.fileSize());
        doc.setSha256(stored.sha256()); doc.setScanStatus("PASSED");
        if (manualDocMapper.replaceFile(doc, expectedVersion) != 1) {
            storageService.deleteQuietly(stored.objectKey()); conflict();
        }
        auditService.success("MANUAL_FILE_REPLACE", "MANUAL", id, Map.of("fileName", stored.fileName()));
        return manual(id);
    }

    @Transactional
    public AdminManagementVOs.ManualVO setManualStatus(String id, String status, int expectedVersion) {
        ManualDocPO doc = requireManual(id);
        if (!Set.of("PUBLISHED", "ARCHIVED").contains(status)) {
            throw new BusinessException("CONTENT_STATUS_INVALID", 400, "手册状态操作无效");
        }
        if (status.equals("PUBLISHED") && !"PASSED".equals(doc.getScanStatus())) {
            throw new BusinessException("MANUAL_SCAN_INCOMPLETE", 409, "文件扫描未通过，不能发布");
        }
        if (status.equals("PUBLISHED") && !storageService.isAvailable(doc.getObjectKey())) {
            throw new BusinessException("MANUAL_FILE_NOT_FOUND", 409,
                    "手册原文件不存在或属于旧版存储格式，请重新上传文件后再发布");
        }
        if (manualDocMapper.updateStatus(doc.getId(), status,
                status.equals("PUBLISHED") ? now() : null, expectedVersion) != 1) conflict();
        auditService.success("MANUAL_" + status, "MANUAL", id, Map.of("status", status));
        return manual(id);
    }

    public ManualDownload downloadManual(String id) {
        ManualDocPO doc = requireManual(id);
        Path path = storageService.load(doc.getObjectKey());
        return new ManualDownload(new FileSystemResource(path), doc.getFileName(), doc.getContentType());
    }

    public PageVO<AdminManagementVOs.AuditVO> audits(AdminDTOs.AuditQueryDTO query) {
        int p = page(query.page() == null ? 1 : query.page());
        int size = pageSize(query.pageSize() == null ? 20 : query.pageSize());
        List<OperationAuditPO> rows = operationAuditMapper.selectPage(query.operatorId(), query.resourceType(),
                query.action(), query.requestId(), query.from(), query.to(), (p - 1) * size, size);
        return new PageVO<>(rows.stream().map(this::auditVO).toList(),
                operationAuditMapper.countPage(query.operatorId(), query.resourceType(), query.action(),
                        query.requestId(), query.from(), query.to()), p, size);
    }

    private AdminManagementVOs.AdminListItemVO adminVO(AdminUserPO value) {
        return new AdminManagementVOs.AdminListItemVO(value.getPublicId(), value.getUsername(),
                value.getDisplayName(), value.getRoleCode(), value.getStatus(), value.getFailedCount(),
                instant(value.getLockedUntil()), value.getVersion(), instant(value.getUpdatedAt()));
    }

    private AdminManagementVOs.TicketSummaryVO ticketSummary(TicketPO value) {
        CustomerUserPO customer = customerUserMapper.selectById(value.getUserId());
        AdminUserPO assignee = value.getAssigneeId() == null ? null : adminUserMapper.selectById(value.getAssigneeId());
        return new AdminManagementVOs.TicketSummaryVO(value.getPublicId(), value.getTitle(), value.getCategory(),
                customer == null ? "未知用户" : customer.getNickname(), value.getStatus(),
                assignee == null ? null : assignee.getPublicId(), assignee == null ? "未分配" : assignee.getDisplayName(),
                value.getVersion(), instant(value.getCreatedAt()), instant(value.getUpdatedAt()));
    }

    private AdminManagementVOs.TicketDetailVO ticketDetail(TicketPO value) {
        CustomerUserPO customer = customerUserMapper.selectById(value.getUserId());
        AdminUserPO assignee = value.getAssigneeId() == null ? null : adminUserMapper.selectById(value.getAssigneeId());
        return new AdminManagementVOs.TicketDetailVO(value.getPublicId(), value.getTitle(), value.getCategory(),
                value.getDeviceBrand(), value.getDeviceModel(), value.getDescription(), value.getContact(),
                customer == null ? "未知用户" : customer.getNickname(), value.getStatus(),
                assignee == null ? null : assignee.getPublicId(), assignee == null ? "未分配" : assignee.getDisplayName(),
                value.getResolution(), value.getVersion(), instant(value.getCreatedAt()), instant(value.getUpdatedAt()),
                replyMapper.selectByTicketId(value.getId()).stream().map(r -> new AdminManagementVOs.TicketReplyVO(
                        r.getPublicId(), r.getSenderType(), r.getSenderNameSnapshot(), r.getContent(), instant(r.getCreatedAt()))).toList(),
                attachmentMapper.selectByTicketId(value.getId()).stream().map(a -> new AdminManagementVOs.TicketAttachmentVO(
                        a.getPublicId(), a.getFileName(), a.getContentType(), a.getFileSize(), a.getScanStatus())).toList(),
                historyMapper.selectByTicketId(value.getId()).stream().map(h -> new AdminManagementVOs.TicketHistoryVO(
                        h.getPublicId(), h.getFromStatus(), h.getToStatus(), h.getTitle(), h.getReason(), instant(h.getCreatedAt()))).toList());
    }

    private AdminManagementVOs.CategoryVO categoryVO(FaqCategoryPO value) {
        return new AdminManagementVOs.CategoryVO(value.getPublicId(), value.getName(), value.getSortOrder(),
                value.getStatus(), value.getVersion(), instant(value.getUpdatedAt()));
    }
    private AdminManagementVOs.CategoryVO categoryVO(ManualCategoryPO value) {
        return new AdminManagementVOs.CategoryVO(value.getPublicId(), value.getName(), value.getSortOrder(),
                value.getStatus(), value.getVersion(), instant(value.getUpdatedAt()));
    }
    private AdminManagementVOs.FaqVO faqVO(FaqItemPO value) {
        String categoryId = faqCategoryMapper.selectAll().stream().filter(c -> c.getId().equals(value.getCategoryId()))
                .map(FaqCategoryPO::getPublicId).findFirst().orElse("");
        return new AdminManagementVOs.FaqVO(value.getPublicId(), categoryId, value.getTitle(), value.getQuestion(),
                value.getAnswer(), value.getSummary(), value.getKeywords(), value.getStatus(),
                Boolean.TRUE.equals(value.getIsTop()), value.getHotCount(), value.getVersion(),
                instant(value.getPublishedAt()), instant(value.getUpdatedAt()));
    }
    private AdminManagementVOs.ManualVO manualVO(ManualDocPO value) {
        String categoryId = manualCategoryMapper.selectAll().stream().filter(c -> c.getId().equals(value.getCategoryId()))
                .map(ManualCategoryPO::getPublicId).findFirst().orElse("");
        return new AdminManagementVOs.ManualVO(value.getPublicId(), categoryId, value.getTitle(), value.getSummary(),
                value.getFileName(), value.getContentType(), value.getFileSize(), value.getScanStatus(),
                value.getStatus(), value.getVersionNo(), value.getVersion(), instant(value.getPublishedAt()), instant(value.getUpdatedAt()));
    }
    private AdminManagementVOs.AuditVO auditVO(OperationAuditPO value) {
        return new AdminManagementVOs.AuditVO(value.getRequestId(), value.getActorId(), value.getActionName(),
                value.getResourceType(), value.getResourceId(), value.getResult(), value.getDetailJson(),
                value.getIpAddress(), instant(value.getCreatedAt()));
    }

    private void history(TicketPO ticket, String target, String title, String reason) {
        AuthPrincipal principal = AuthContext.required();
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
        historyMapper.insert(TicketStatusHistoryPO.builder().publicId(id("his_"))
                .ticketId(ticket.getId()).fromStatus(ticket.getStatus()).toStatus(target)
                .operatorType("ADMIN").operatorId(principal.userId()).title(title).reason(nullToEmpty(reason))
                .requestId(request == null ? "req_unknown" : RequestIdFilter.get(request)).createdAt(now()).build());
    }

    private boolean validTransition(String from, String to) {
        return switch (from) {
            case "PROCESSING" -> Set.of("WAITING_USER", "RESOLVED").contains(to);
            case "WAITING_USER" -> Set.of("PROCESSING", "RESOLVED").contains(to);
            case "RESOLVED" -> "CLOSED".equals(to);
            default -> false;
        };
    }

    private TicketPO requireTicket(String id) { TicketPO value=ticketMapper.selectByPublicId(id); if(value==null) notFound("工单"); return value; }
    private AdminUserPO requireAdmin(String id) { AdminUserPO value=adminUserMapper.selectByPublicId(id); if(value==null) notFound("管理员"); return value; }
    private FaqCategoryPO requireFaqCategory(String id) { FaqCategoryPO value=faqCategoryMapper.selectByPublicId(id); if(value==null) notFound("FAQ 分类"); return value; }
    private FaqItemPO requireFaq(String id) { FaqItemPO value=faqItemMapper.selectByPublicId(id); if(value==null) notFound("FAQ"); return value; }
    private ManualCategoryPO requireManualCategory(String id) { ManualCategoryPO value=manualCategoryMapper.selectByPublicId(id); if(value==null) notFound("手册分类"); return value; }
    private ManualDocPO requireManual(String id) { ManualDocPO value=manualDocMapper.selectByPublicId(id); if(value==null) notFound("手册"); return value; }
    private void notFound(String name) { throw new BusinessException("RESOURCE_NOT_FOUND",404,name+"不存在"); }
    private void conflict() { throw new BusinessException("RESOURCE_VERSION_CONFLICT",409,"资源已被其他操作更新，请刷新后重试"); }
    private void inUse() { throw new BusinessException("CATEGORY_IN_USE",409,"分类下仍有内容，不能删除"); }
    private int page(int value) { return Math.max(1,value); }
    private int pageSize(int value) { return Math.max(1,Math.min(100,value)); }
    private int defaultInt(Integer value) { return value==null?0:value; }
    private String defaultStatus(String value) { return value==null||value.isBlank()?"ENABLED":value; }
    private String trim(String value) { return value==null?null:value.trim(); }
    private String nullToEmpty(String value) { return value==null?"":value.trim(); }
    private String required(String value,String message) { if(value==null||value.isBlank()) throw new BusinessException("VALIDATION_ERROR",400,message); return value.trim(); }
    private String id(String prefix) { return prefix+UUID.randomUUID().toString().replace("-",""); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private Instant instant(LocalDateTime value) { return value==null?null:value.toInstant(ZoneOffset.UTC); }

    public record ManualDownload(FileSystemResource resource, String fileName, String contentType) { }
}
