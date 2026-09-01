package com.xc.agent.service.impl;

import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.mapper.FaqCategoryMapper;
import com.xc.agent.mapper.FaqItemMapper;
import com.xc.agent.model.dto.content.ContentDTOs;
import com.xc.agent.model.po.FaqCategoryPO;
import com.xc.agent.model.po.FaqItemPO;
import com.xc.agent.model.vo.content.ContentVOs;
import com.xc.agent.service.content.FaqCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentServiceImplTest {
    @Mock FaqCategoryMapper categoryMapper;
    @Mock FaqItemMapper faqMapper;
    @Mock FaqCacheService cache;
    private ContentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContentServiceImpl(categoryMapper, faqMapper, cache);
    }

    @Test
    void returnsCachedPageWithoutQueryingMysql() {
        ContentDTOs.FaqQueryDTO query = new ContentDTOs.FaqQueryDTO("打印机", null, 1, 20);
        PageVO<ContentVOs.KnowledgeItemVO> cached = new PageVO<>(List.of(), 0, 1, 20);
        when(cache.pageKey("打印机", null, 1, 20)).thenReturn("faq:key");
        when(cache.getPage("faq:key")).thenReturn(Optional.of(cached));

        assertThat(service.listFaq(query)).isSameAs(cached);
        verify(faqMapper, never()).selectPublishedPage(any(), any(), anyInt(), anyInt());
    }

    @Test
    void queriesMysqlAndBackfillsRedisOnCacheMiss() {
        ContentDTOs.FaqQueryDTO query = new ContentDTOs.FaqQueryDTO(null, null, 1, 20);
        FaqCategoryPO category = FaqCategoryPO.builder().id(1L).publicId("faqcat_system")
                .name("系统问题").sortOrder(1).status("ENABLED").build();
        FaqItemPO item = FaqItemPO.builder().id(2L).publicId("faq_boot")
                .categoryId(1L).title("无法启动").question("电脑无法启动怎么办")
                .summary("启动故障排查").hotCount(3).updatedAt(LocalDateTime.of(2026, 8, 26, 1, 0))
                .build();
        when(cache.pageKey(null, null, 1, 20)).thenReturn("faq:key");
        when(cache.getPage("faq:key")).thenReturn(Optional.empty());
        when(categoryMapper.selectEnabled()).thenReturn(List.of(category));
        when(faqMapper.selectPublishedPage(null, null, 0, 20)).thenReturn(List.of(item));
        when(faqMapper.countPublishedPage(null, null)).thenReturn(1L);

        PageVO<ContentVOs.KnowledgeItemVO> result = service.listFaq(query);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items().getFirst().title()).isEqualTo("无法启动");
        assertThat(result.items().getFirst().category()).isEqualTo("系统问题");
        verify(cache).putPage(eq("faq:key"), any());
    }

    @Test
    void returnsCachedFaqDetailWithoutQueryingMysql() {
        ContentVOs.FaqDetailVO cached = new ContentVOs.FaqDetailVO(
                "faq_boot", "faqcat_system", "系统问题", "无法启动", "怎么办", "请检查电源",
                "启动排查", 3, java.time.Instant.parse("2026-08-26T01:00:00Z"));
        when(cache.getDetail("faq_boot")).thenReturn(Optional.of(cached));

        assertThat(service.getFaq("faq_boot")).isSameAs(cached);

        verify(faqMapper, never()).selectPublishedByPublicId(any());
    }

    @Test
    void queriesPublishedFaqDetailAndBackfillsRedisOnMiss() {
        FaqCategoryPO category = FaqCategoryPO.builder().id(1L).publicId("faqcat_system")
                .name("系统问题").sortOrder(1).status("ENABLED").build();
        FaqItemPO item = FaqItemPO.builder().id(2L).publicId("faq_boot")
                .categoryId(1L).title("无法启动").question("电脑无法启动怎么办")
                .answer("请检查电源和指示灯").summary("启动故障排查").hotCount(3)
                .updatedAt(LocalDateTime.of(2026, 8, 26, 1, 0)).build();
        when(cache.getDetail("faq_boot")).thenReturn(Optional.empty());
        when(faqMapper.selectPublishedByPublicId("faq_boot")).thenReturn(item);
        when(categoryMapper.selectEnabled()).thenReturn(List.of(category));

        ContentVOs.FaqDetailVO result = service.getFaq("faq_boot");

        assertThat(result.answer()).isEqualTo("请检查电源和指示灯");
        assertThat(result.categoryName()).isEqualTo("系统问题");
        verify(cache).putDetail("faq_boot", result);
    }

    @Test
    void returnsNotFoundWhenFaqIsNotPublished() {
        when(cache.getDetail("faq_draft")).thenReturn(Optional.empty());
        when(faqMapper.selectPublishedByPublicId("faq_draft")).thenReturn(null);

        assertThatThrownBy(() -> service.getFaq("faq_draft"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("FAQ_NOT_FOUND");

        verify(cache, never()).putDetail(eq("faq_draft"), any());
    }

    @Test
    void returnsNotFoundWhenFaqCategoryIsDisabled() {
        FaqItemPO item = FaqItemPO.builder().id(2L).publicId("faq_disabled")
                .categoryId(9L).title("停用分类内容").question("问题").answer("答案")
                .summary("").hotCount(0).updatedAt(LocalDateTime.of(2026, 8, 26, 1, 0))
                .build();
        when(cache.getDetail("faq_disabled")).thenReturn(Optional.empty());
        when(faqMapper.selectPublishedByPublicId("faq_disabled")).thenReturn(item);
        when(categoryMapper.selectEnabled()).thenReturn(List.of());

        assertThatThrownBy(() -> service.getFaq("faq_disabled"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("FAQ_NOT_FOUND");

        verify(cache, never()).putDetail(eq("faq_disabled"), any());
    }
}
