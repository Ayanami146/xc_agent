package com.xc.agent.model.dto.content;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class ContentDTOs {
    private ContentDTOs() {
    }

    public record FaqQueryDTO(
            @Size(max = 100) String keyword,
            String categoryId,
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer pageSize
    ) {
    }

    public record ManualQueryDTO(
            @Size(max = 100) String keyword,
            String categoryId,
            @Min(1) Integer page,
            @Min(1) @Max(100) Integer pageSize
    ) {
    }
}
