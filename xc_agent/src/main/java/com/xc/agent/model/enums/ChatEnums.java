package com.xc.agent.model.enums;

public final class ChatEnums {
    private ChatEnums() {
    }

    public enum SessionStatus { ACTIVE, ARCHIVED }
    public enum RequestStatus { ACCEPTED, RUNNING, SUCCEEDED, FAILED, CANCELLED, INTERRUPTED }
    public enum MessageRole { user, assistant }
    public enum MessageStatus { COMPLETED, STREAMING, INTERRUPTED, FAILED }
    public enum Stage { queued, safety, intent, retrieval, generation, validation }
    public enum Feedback { up, down }
    public enum StreamEvent { meta, status, delta, citation, usage, heartbeat, done, error }
}
