package com.xc.agent.service.auth;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmsCodeServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SmsCodeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.setSeed(123L);
        AuthProperties properties = new AuthProperties(Duration.ofHours(2), Duration.ofDays(30),
                Duration.ofMinutes(5), "test-secret-that-is-longer-than-thirty-two-bytes-2026",
                "XC_REFRESH_TOKEN", "/api/v1/auth", false);
        service = new SmsCodeService(redisTemplate, new CryptoService(secureRandom), properties, secureRandom);
    }

    @Test
    void storesSixDigitCodeForFiveMinutes() {
        service.send("13800138000");

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), code.capture(), eq(Duration.ofMinutes(5)));
        assertThat(code.getValue()).matches("\\d{6}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumesOnlyMatchingCode() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("123456")))
                .thenReturn(1L);

        service.verifyAndConsume("13800138000", "123456");

        verify(redisTemplate).execute(any(RedisScript.class), any(List.class), eq("123456"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsWrongOrExpiredCode() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), eq("000000")))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.verifyAndConsume("13800138000", "000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("AUTH_SMS_CODE_INVALID");
    }
}
