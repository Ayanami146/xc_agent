package com.xc.agent.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketPO {
    private Long id;
    private String publicId;
    private Long userId;
    private String title;
    private String category;
    private String deviceBrand;
    private String deviceModel;
    private String description;
    private String contact;
    private String status;
    private Long assigneeId;
    private String resolution;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
