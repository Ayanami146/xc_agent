package com.xc.agent.common.api;

public record ApiResponse<T>(String requestId, T data) {
}
