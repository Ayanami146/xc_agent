package com.xc.agent.service.content;

import com.xc.agent.common.api.PageVO;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.FaqCacheProperties;
import com.xc.agent.model.vo.content.ContentVOs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class FaqCacheService {
    private static final TypeReference<List<ContentVOs.CategoryVO>> CATEGORY_TYPE = new TypeReference<>() { };
    private static final TypeReference<PageVO<ContentVOs.KnowledgeItemVO>> PAGE_TYPE = new TypeReference<>() { };
    private static final TypeReference<ContentVOs.FaqDetailVO> DETAIL_TYPE = new TypeReference<>() { };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;
    private final FaqCacheProperties properties;

    public FaqCacheService(StringRedisTemplate redis, ObjectMapper objectMapper,
                           CryptoService cryptoService, FaqCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cryptoService = cryptoService;
        this.properties = properties;
    }

    public Optional<List<ContentVOs.CategoryVO>> getCategories() {
        return read(categoriesKey(), CATEGORY_TYPE);
    }

    public void putCategories(List<ContentVOs.CategoryVO> value) {
        write(categoriesKey(), value);
    }

    public String pageKey(String keyword, String categoryId, int page, int pageSize) {
        String canonical = normalize(keyword) + "|" + normalize(categoryId) + "|" + page + "|" + pageSize;
        return prefix() + "page:" + cryptoService.sha256Hex(canonical);
    }

    public Optional<PageVO<ContentVOs.KnowledgeItemVO>> getPage(String key) {
        return read(key, PAGE_TYPE);
    }

    public void putPage(String key, PageVO<ContentVOs.KnowledgeItemVO> value) {
        write(key, value);
    }

    /**
     * 读取单条 FAQ 详情。这里使用的是 Java 业务 Redis，绝不能改为 Agent 的
     * AGENT_REDIS_URL；两套 Redis 服务承担完全不同的职责和生命周期。
     */
    public Optional<ContentVOs.FaqDetailVO> getDetail(String faqId) {
        return read(detailKey(faqId), DETAIL_TYPE);
    }

    public void putDetail(String faqId, ContentVOs.FaqDetailVO value) {
        write(detailKey(faqId), value);
    }

    public void evictAllAfterCommit() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictAll();
                }
            });
            return;
        }
        evictAll();
    }

    public void evictAll() {
        try {
            Set<String> keys = redis.opsForSet().members(indexKey());
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete(indexKey());
            log.info("FAQ Redis cache cleared");
        } catch (DataAccessException exception) {
            log.warn("Failed to clear FAQ Redis cache; entries will expire by TTL", exception);
        }
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                log.info("FAQ cache miss, key={}", key);
                return Optional.empty();
            }
            log.info("FAQ cache hit, key={}", key);
            return Optional.of(objectMapper.readValue(json, type));
        } catch (DataAccessException | JacksonException exception) {
            log.warn("FAQ cache read failed, fallback to MySQL, key={}", key, exception);
            return Optional.empty();
        }
    }

    private void write(String key, Object value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), properties.ttl());
            redis.opsForSet().add(indexKey(), key);
        } catch (DataAccessException | JacksonException exception) {
            log.warn("FAQ cache write failed, key={}", key, exception);
        }
    }

    private String categoriesKey() {
        return prefix() + "categories";
    }

    private String detailKey(String faqId) {
        return prefix() + "detail:" + faqId;
    }

    private String indexKey() {
        return prefix() + "keys";
    }

    private String prefix() {
        String value = properties.keyPrefix();
        return value.endsWith(":") ? value : value + ":";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
