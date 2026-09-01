package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ManualDocPO {
    private Long id;
    private String publicId;
    private Long categoryId;
    private String title;
    private String summary;
    private String parsedText;
    private String objectKey;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String scanStatus;
    private String status;
    private Integer versionNo;
    private LocalDateTime publishedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
