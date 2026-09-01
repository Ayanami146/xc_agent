package com.xc.agent.model.dto.ticket;

import com.xc.agent.model.enums.TicketEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TicketDTOs {
    private TicketDTOs() {
    }

    public record TicketQueryDTO(
            @Size(max = 100) String keyword,
            TicketEnums.Status status,
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer pageSize
    ) {
    }

    public record CreateTicketDTO(
            @NotBlank @Size(min = 4, max = 60) String title,
            @NotBlank @Size(max = 50) String category,
            @NotBlank @Size(max = 50) String deviceBrand,
            @NotBlank @Size(max = 100) String deviceModel,
            @NotBlank @Size(min = 10, max = 2000) String description,
            @NotBlank @Size(max = 100) String contact,
            @Size(max = 5) List<String> attachmentIds
    ) {
    }

    public record CreateTicketReplyDTO(
            @NotBlank @Size(max = 1000) String content,
            @Size(max = 5) List<String> attachmentIds
    ) {
    }

    public record ReopenTicketDTO(
            @NotBlank @Size(max = 500) String reason
    ) {
    }
}
