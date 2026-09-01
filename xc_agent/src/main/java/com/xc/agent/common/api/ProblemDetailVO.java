package com.xc.agent.common.api;

import java.util.List;

public record ProblemDetailVO(
        String type,
        String title,
        int status,
        String code,
        String detail,
        String requestId,
        List<FieldErrorVO> errors
) {
}
