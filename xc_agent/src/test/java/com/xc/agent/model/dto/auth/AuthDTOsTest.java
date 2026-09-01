package com.xc.agent.model.dto.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AuthDTOsTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void deserializesPasswordLoginByMode() throws Exception {
        AuthDTOs.LoginDTO login = jsonMapper.readValue("""
                {"mode":"password","account":"demo","password":"123456","rememberDevice":true}
                """, AuthDTOs.LoginDTO.class);

        assertThat(login).isInstanceOf(AuthDTOs.PasswordLoginDTO.class);
        assertThat(login.rememberDevice()).isTrue();
        assertThat(validator.validate(login)).isEmpty();
    }

    @Test
    void deserializesSmsLoginByMode() throws Exception {
        AuthDTOs.LoginDTO login = jsonMapper.readValue("""
                {"mode":"sms","phone":"13800138000","code":"012345","rememberDevice":false}
                """, AuthDTOs.LoginDTO.class);

        assertThat(login).isInstanceOf(AuthDTOs.SmsLoginDTO.class);
        assertThat(validator.validate(login)).isEmpty();
    }

    @Test
    void rejectsInvalidPhoneAndCode() {
        AuthDTOs.SmsLoginDTO login = new AuthDTOs.SmsLoginDTO(
                com.xc.agent.model.enums.AuthEnums.LoginMode.sms, "123", "12", false);

        assertThat(validator.validate(login)).hasSize(2);
    }
}
