package com.xc.agent.model.vo.content;

import com.xc.agent.model.enums.ContentEnums;

import java.time.Instant;

public final class ContentVOs {
    private ContentVOs() {
    }

    public record CategoryVO(String id, String name, int sortOrder) {
    }

    public record KnowledgeItemVO(String id, ContentEnums.KnowledgeKind kind, String title,
                                  String summary, String category, Instant updatedAt,
                                  Integer hotCount, String question) {
    }

    public record FaqDetailVO(String id, String categoryId, String categoryName, String title,
                              String question, String answer, String summary, int hotCount,
                              Instant updatedAt) {
    }

    public record ManualDetailVO(String id, String categoryId, String categoryName, String title,
                                 String summary, String fileName, String contentType, long fileSize,
                                 int versionNo, Instant updatedAt) {
    }

    public record DownloadGrantVO(String url, Instant expiresAt) {
    }
}
