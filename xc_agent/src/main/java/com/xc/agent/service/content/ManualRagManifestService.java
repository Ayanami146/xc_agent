package com.xc.agent.service.content;

import com.xc.agent.mapper.ManualDocMapper;
import com.xc.agent.model.vo.internal.InternalVOs;
import com.xc.agent.service.admin.ManualStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提供可重建 RAG 索引的已发布手册清单。
 *
 * <p>MySQL 与本机原文件始终是真相源，Chroma 只是 Python Agent 的派生索引。本服务
 * 不调用 Agent、不写 Chroma，也不使用 FAQ Redis，从而避免跨服务双写。</p>
 */
@Service
@Slf4j
public class ManualRagManifestService {
    private final ManualDocMapper manualDocMapper;
    private final ManualStorageService storageService;

    public ManualRagManifestService(ManualDocMapper manualDocMapper,
                                    ManualStorageService storageService) {
        this.manualDocMapper = manualDocMapper;
        this.storageService = storageService;
    }

    public List<InternalVOs.RagManualVO> listPublishedManuals() {
        return manualDocMapper.selectPublishedForRag().stream()
                .filter(doc -> {
                    boolean available = storageService.isAvailable(doc.getObjectKey());
                    if (!available) {
                        log.warn("忽略缺少受管原文件的已发布手册，documentId={} objectKey={}",
                                doc.getPublicId(), doc.getObjectKey());
                    }
                    return available;
                })
                .map(doc -> new InternalVOs.RagManualVO(
                        doc.getId(), doc.getPublicId(), doc.getTitle(), doc.getSummary(),
                        doc.getObjectKey(), doc.getFileName(), doc.getContentType(), doc.getSha256(),
                        doc.getVersionNo(), doc.getVersion()))
                .toList();
    }
}
