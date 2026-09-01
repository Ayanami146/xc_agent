package com.xc.agent.model.enums;

public final class AuthEnums {
    private AuthEnums() {
    }

    public enum UserStatus { ACTIVE, DISABLED, LOCKED }
    public enum AdminRole { SUPPORT, ADMIN }
    public enum SubjectType { CUSTOMER, ADMIN }
    public enum SmsPurpose { LOGIN, RESET_PASSWORD }
    public enum LoginMode { password, sms }
    public enum LoginResult { SUCCESS, FAILED }
}
