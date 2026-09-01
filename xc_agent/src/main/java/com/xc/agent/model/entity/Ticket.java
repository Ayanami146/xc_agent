package com.xc.agent.model.entity;

import com.xc.agent.model.enums.TicketEnums;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Ticket {
    private Long id;
    private String publicId;
    private Long userId;
    private String title;
    private String category;
    private String deviceBrand;
    private String deviceModel;
    private String description;
    private String contact;
    private TicketEnums.Status status;
    private Long assigneeId;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
