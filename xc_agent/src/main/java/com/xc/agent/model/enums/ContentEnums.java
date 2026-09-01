package com.xc.agent.model.enums;

public final class ContentEnums {
    private ContentEnums() {
    }

    public enum EnableStatus { ENABLED, DISABLED }
    public enum PublishStatus { DRAFT, PUBLISHED, ARCHIVED }
    public enum ScanStatus { PENDING, PASSED, REJECTED }
    public enum KnowledgeKind { FAQ, MANUAL }
}
