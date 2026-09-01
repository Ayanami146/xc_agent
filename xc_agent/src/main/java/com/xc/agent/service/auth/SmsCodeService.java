package com.xc.agent.service.auth;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

@Service
public class SmsCodeService {
    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private static final String KEY_PREFIX = "auth:sms:login:";
    private static final DefaultRedisScript<Long> VERIFY_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final CryptoService cryptoService;
    private final AuthProperties properties;
    private final SecureRandom secureRandom;

    public SmsCodeService(StringRedisTemplate redisTemplate, CryptoService cryptoService,
                          AuthProperties properties, SecureRandom secureRandom) {
        this.redisTemplate = redisTemplate;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.secureRandom = secureRandom;
    }

    public void send(String phone) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        redisTemplate.opsForValue().set(key(phone), code, properties.smsCodeTtl());
        log.info("本地登录验证码 phone={} code={} expiresIn={}s",
                maskPhone(phone), code, properties.smsCodeTtl().toSeconds());
    }

    public void verifyAndConsume(String phone, String code) {
        Long matched = redisTemplate.execute(VERIFY_AND_DELETE, List.of(key(phone)), code);
        if (!Long.valueOf(1L).equals(matched)) {
            throw new BusinessException("AUTH_SMS_CODE_INVALID", 401, "验证码错误或已过期");
        }
    }

    private String key(String phone) {
        return KEY_PREFIX + cryptoService.sha256Hex(phone);
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
