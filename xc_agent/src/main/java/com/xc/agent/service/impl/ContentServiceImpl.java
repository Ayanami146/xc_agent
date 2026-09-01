package com.xc.agent.service.impl;

import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.mapper.FaqCategoryMapper;
import com.xc.agent.mapper.FaqItemMapper;
import com.xc.agent.model.dto.content.ContentDTOs;
import com.xc.agent.model.enums.ContentEnums;
import com.xc.agent.model.po.FaqCategoryPO;
import com.xc.agent.model.po.FaqItemPO;
import com.xc.agent.model.vo.content.ContentVOs;
import com.xc.agent.service.ContentService;
import com.xc.agent.service.content.FaqCacheService;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ContentServiceImpl implements ContentService {
    private final FaqCategoryMapper categoryMapper;
    private final FaqItemMapper faqMapper;
    private final FaqCacheService cache;

    public ContentServiceImpl(FaqCategoryMapper categoryMapper, FaqItemMapper faqMapper,
                              FaqCacheService cache) {
        this.categoryMapper = categoryMapper;
        this.faqMapper = faqMapper;
        this.cache = cache;
    }

    @Override
    public List<ContentVOs.CategoryVO> listFaqCategories() {
        return cache.getCategories().orElseGet(() -> {
            List<ContentVOs.CategoryVO> result = categoryMapper.selectEnabled().stream()
                    .map(value -> new ContentVOs.CategoryVO(
                            value.getPublicId(), value.getName(), value.getSortOrder()))
                    .toList();
            cache.putCategories(result);
            return result;
        });
    }

    @Override
    public PageVO<ContentVOs.KnowledgeItemVO> listFaq(ContentDTOs.FaqQueryDTO query) {
        String keyword = trim(query.keyword());
        String categoryId = trim(query.categoryId());
        int page = query.page() == null ? 1 : query.page();
        int pageSize = query.pageSize() == null ? 20 : query.pageSize();
        String key = cache.pageKey(keyword, categoryId, page, pageSize);
        return cache.getPage(key).orElseGet(() -> loadFromDatabase(keyword, categoryId, page, pageSize, key));
    }

    @Override
    public ContentVOs.FaqDetailVO getFaq(String faqId) {
        String normalizedId = trim(faqId);
        if (normalizedId == null) {
            throw new BusinessException("FAQ_NOT_FOUND", 404, "FAQ 不存在或尚未发布");
        }
        return cache.getDetail(normalizedId).orElseGet(() -> loadDetailFromDatabase(normalizedId));
    }

    /**
     * Redis 未命中时只查询已发布且分类启用的 FAQ。详情成功构造后再写缓存，因此草稿、
     * 已归档内容和禁用分类不会进入用户缓存。
     */
    private ContentVOs.FaqDetailVO loadDetailFromDatabase(String faqId) {
        FaqItemPO item = faqMapper.selectPublishedByPublicId(faqId);
        if (item == null) {
            throw new BusinessException("FAQ_NOT_FOUND", 404, "FAQ 不存在或尚未发布");
        }
        FaqCategoryPO category = categoryMapper.selectEnabled().stream()
                .filter(value -> value.getId().equals(item.getCategoryId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "FAQ_NOT_FOUND", 404, "FAQ 不存在或尚未发布"));
        ContentVOs.FaqDetailVO result = new ContentVOs.FaqDetailVO(
                item.getPublicId(), category.getPublicId(), category.getName(), item.getTitle(),
                item.getQuestion(), item.getAnswer(), item.getSummary(), item.getHotCount(),
                item.getUpdatedAt().toInstant(ZoneOffset.UTC));
        cache.putDetail(faqId, result);
        return result;
    }

    private PageVO<ContentVOs.KnowledgeItemVO> loadFromDatabase(
            String keyword, String categoryId, int page, int pageSize, String cacheKey) {
        List<FaqCategoryPO> categories = categoryMapper.selectEnabled();
        Map<Long, FaqCategoryPO> categoryById = categories.stream()
                .collect(Collectors.toMap(FaqCategoryPO::getId, Function.identity()));
        Long internalCategoryId = null;
        if (categoryId != null) {
            internalCategoryId = categories.stream()
                    .filter(category -> categoryId.equals(category.getPublicId()))
                    .map(FaqCategoryPO::getId)
                    .findFirst()
                    .orElse(-1L);
        }
        List<FaqItemPO> rows = faqMapper.selectPublishedPage(
                keyword, internalCategoryId, (page - 1) * pageSize, pageSize);
        List<ContentVOs.KnowledgeItemVO> items = rows.stream()
                .map(item -> toKnowledgeItem(item, categoryById.get(item.getCategoryId())))
                .toList();
        PageVO<ContentVOs.KnowledgeItemVO> result = new PageVO<>(items,
                faqMapper.countPublishedPage(keyword, internalCategoryId), page, pageSize);
        cache.putPage(cacheKey, result);
        return result;
    }

    private ContentVOs.KnowledgeItemVO toKnowledgeItem(FaqItemPO item, FaqCategoryPO category) {
        return new ContentVOs.KnowledgeItemVO(
                item.getPublicId(), ContentEnums.KnowledgeKind.FAQ, item.getTitle(),
                item.getSummary(), category == null ? "" : category.getName(),
                item.getUpdatedAt().toInstant(ZoneOffset.UTC), item.getHotCount(), item.getQuestion());
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
