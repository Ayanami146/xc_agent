package com.xc.agent.model.vo.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class InternalVOs {
    private InternalVOs() {
    }

    public record AcceptedJobVO(String jobId, String status) {
    }

    public record RetrievalChunkVO(Long sourceId, String title, String content,
                                   double rawScore, double rerankScore, Map<String, Object> metadata) {
    }

    /**
     * Java 向 Agent 暴露的已发布维修手册清单项。
     *
     * <p>只传对象键而不传任意绝对路径。Agent 使用双方约定的共享目录解析对象键，
     * SHA-256 和资源版本用于判断 Chroma 中的切片是否需要重建。</p>
     */
    public record RagManualVO(Long sourceId, String documentId, String title, String summary,
                              String objectKey, String fileName, String contentType, String sha256,
                              int versionNo, int resourceVersion) {
    }

    public record EvaluationUsageVO(String model, int promptTokens, int completionTokens,
                                    int totalTokens, BigDecimal estimatedCost) {
    }

    public record EvaluationResultVO(String answer, String modelRoute,
                                     List<RetrievalChunkVO> chunks, String refusalReason,
                                     String configVersion, EvaluationUsageVO usage) {
    }

    public record UsageBatchAckVO(String batchId, int acceptedCount, int rejectedCount,
                                  List<String> rejectedCallIds) {
    }
}
