package com.xc.agent.common.security;

import com.xc.agent.model.enums.AuthEnums;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminRoleRequired {
    AuthEnums.AdminRole[] value() default {AuthEnums.AdminRole.ADMIN};
}
