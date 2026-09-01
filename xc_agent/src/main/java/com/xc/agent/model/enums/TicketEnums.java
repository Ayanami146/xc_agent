package com.xc.agent.model.enums;

public final class TicketEnums {
    private TicketEnums() {
    }

    public enum Status { PENDING, PROCESSING, WAITING_USER, RESOLVED, CLOSED }
    public enum SenderType { user, admin }
    public enum BindStatus { TEMP, BOUND, REJECTED }
    public enum OperatorType { SYSTEM, CUSTOMER, ADMIN }
}
