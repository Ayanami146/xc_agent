package com.xc.agent.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final String code;
    private final int status;

    public BusinessException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
