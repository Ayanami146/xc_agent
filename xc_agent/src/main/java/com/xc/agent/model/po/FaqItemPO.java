package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FaqItemPO {
    private Long id;
    private String publicId;
    private Long categoryId;
    private String title;
    private String question;
    private String answer;
    private String summary;
    private String keywords;
    private String status;
    private Boolean isTop;
    private Integer hotCount;
    private LocalDateTime publishedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
