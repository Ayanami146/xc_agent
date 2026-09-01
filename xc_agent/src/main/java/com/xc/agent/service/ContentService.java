package com.xc.agent.service;

import com.xc.agent.common.api.PageVO;
import com.xc.agent.model.dto.content.ContentDTOs;
import com.xc.agent.model.vo.content.ContentVOs;

import java.util.List;

public interface ContentService {
    List<ContentVOs.CategoryVO> listFaqCategories();

    PageVO<ContentVOs.KnowledgeItemVO> listFaq(ContentDTOs.FaqQueryDTO query);

    ContentVOs.FaqDetailVO getFaq(String faqId);
}
