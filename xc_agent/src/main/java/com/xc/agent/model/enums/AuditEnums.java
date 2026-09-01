package com.xc.agent.model.enums;

public final class AuditEnums {
    private AuditEnums() {
    }

    public enum ActorType { CUSTOMER, ADMIN, SYSTEM }
    public enum Result { SUCCESS, FAILED }
}
