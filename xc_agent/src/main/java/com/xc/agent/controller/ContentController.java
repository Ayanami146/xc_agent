package com.xc.agent.controller;

import com.xc.agent.common.api.ApiResponse;
import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.web.RequestIdFilter;
import com.xc.agent.model.dto.content.ContentDTOs;
import com.xc.agent.model.vo.content.ContentVOs;
import com.xc.agent.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ContentController {
    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    @GetMapping("/faq/categories")
    public ApiResponse<List<ContentVOs.CategoryVO>> faqCategories(HttpServletRequest request) {
        return new ApiResponse<>(RequestIdFilter.get(request), contentService.listFaqCategories());
    }

    @GetMapping("/faq")
    public ApiResponse<PageVO<ContentVOs.KnowledgeItemVO>> faqs(
            @Valid @ModelAttribute ContentDTOs.FaqQueryDTO query, HttpServletRequest request) {
        return new ApiResponse<>(RequestIdFilter.get(request), contentService.listFaq(query));
    }

    @GetMapping("/faq/{faqId}")
    public ApiResponse<ContentVOs.FaqDetailVO> faq(
            @PathVariable String faqId, HttpServletRequest request) {
        return new ApiResponse<>(RequestIdFilter.get(request), contentService.getFaq(faqId));
    }
}
