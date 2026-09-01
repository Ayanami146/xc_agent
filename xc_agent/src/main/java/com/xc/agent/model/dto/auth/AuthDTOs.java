package com.xc.agent.model.dto.auth;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.xc.agent.model.enums.AuthEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDTOs {
    private AuthDTOs() {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "mode", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = PasswordLoginDTO.class, name = "password"),
            @JsonSubTypes.Type(value = SmsLoginDTO.class, name = "sms")
    })
    public sealed interface LoginDTO permits PasswordLoginDTO, SmsLoginDTO {
        boolean rememberDevice();
    }

    public record PasswordLoginDTO(
            @NotNull AuthEnums.LoginMode mode,
            @NotBlank @Size(max = 100) String account,
            @NotBlank @Size(min = 6, max = 128) String password,
            boolean rememberDevice
    ) implements LoginDTO {
    }

    public record SmsLoginDTO(
            @NotNull AuthEnums.LoginMode mode,
            @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
            @NotBlank @Pattern(regexp = "^\\d{6}$") String code,
            boolean rememberDevice
    ) implements LoginDTO {
    }

    public record SendSmsCodeDTO(
            @NotBlank @Pattern(regexp = "^1\\d{10}$") String phone
    ) {
    }
}
