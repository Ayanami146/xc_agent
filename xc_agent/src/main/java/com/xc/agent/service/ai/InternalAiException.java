package com.xc.agent.service.ai;

/**
 * Agent 调用失败时传回聊天编排层的安全异常。
 *
 * <p>message 可以发送给浏览器；底层响应体、内部地址和异常堆栈只写服务端日志，
 * 防止把 Python 或模型供应商的敏感信息暴露给用户。</p>
 */
public class InternalAiException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public InternalAiException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public InternalAiException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
