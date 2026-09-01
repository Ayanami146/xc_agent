package com.xc.agent.service.content;

import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.FaqCacheProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqCacheServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock ObjectMapper objectMapper;
    @Mock CryptoService cryptoService;
    @Mock SetOperations<String, String> setOperations;

    @Test
    void evictsTrackedFaqKeysAndIndex() {
        FaqCacheService service = new FaqCacheService(redis, objectMapper, cryptoService,
                new FaqCacheProperties(Duration.ofMinutes(10), "xc:faq:v1:"));
        Set<String> keys = Set.of("xc:faq:v1:categories", "xc:faq:v1:page:abc");
        when(redis.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("xc:faq:v1:keys")).thenReturn(keys);

        service.evictAll();

        verify(redis).delete(keys);
        verify(redis).delete("xc:faq:v1:keys");
    }

    @Test
    void defersEvictionUntilTransactionCommit() {
        FaqCacheService service = new FaqCacheService(redis, objectMapper, cryptoService,
                new FaqCacheProperties(Duration.ofMinutes(10), "xc:faq:v1:"));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.evictAllAfterCommit();
            verifyNoInteractions(redis);

            when(redis.opsForSet()).thenReturn(setOperations);
            when(setOperations.members("xc:faq:v1:keys"))
                    .thenReturn(Set.of("xc:faq:v1:detail:faq_1"));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());

            verify(redis).delete(Set.of("xc:faq:v1:detail:faq_1"));
            verify(redis).delete("xc:faq:v1:keys");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
