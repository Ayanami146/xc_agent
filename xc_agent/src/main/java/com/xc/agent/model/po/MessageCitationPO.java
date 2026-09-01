package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MessageCitationPO {
    private Long id;
    private Long messageId;
    private Integer ordinalNo;
    private Long sourceId;
    private String title;
    private String snippet;
    private String sourceLocator;
    private Integer pageNo;
    private BigDecimal score;
    private LocalDateTime createdAt;
}
