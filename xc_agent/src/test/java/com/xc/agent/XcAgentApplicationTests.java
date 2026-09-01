package com.xc.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.xc.agent.mapper.CustomerUserMapper;
import com.xc.agent.mapper.ChatMessageMapper;
import com.xc.agent.mapper.ChatRequestMapper;
import com.xc.agent.mapper.ChatSessionMapper;
import com.xc.agent.mapper.FaqCategoryMapper;
import com.xc.agent.mapper.FaqItemMapper;
import com.xc.agent.mapper.LoginAuditMapper;
import com.xc.agent.mapper.ManualDocMapper;
import com.xc.agent.mapper.MessageCitationMapper;
import com.xc.agent.mapper.MessageFeedbackMapper;
import com.xc.agent.mapper.RefreshTokenMapper;
import com.xc.agent.service.AdminAuthService;
import com.xc.agent.service.admin.AdminManagementService;
import com.xc.agent.service.admin.ManualStorageService;
import com.xc.agent.service.admin.OperationAuditService;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "app.mybatis.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
})
class XcAgentApplicationTests {

    @MockitoBean
    CustomerUserMapper customerUserMapper;

    @MockitoBean
    ChatSessionMapper chatSessionMapper;

    @MockitoBean
    ChatMessageMapper chatMessageMapper;

    @MockitoBean
    ChatRequestMapper chatRequestMapper;

    @MockitoBean
    MessageCitationMapper messageCitationMapper;

    @MockitoBean
    MessageFeedbackMapper messageFeedbackMapper;

    @MockitoBean
    TransactionTemplate transactionTemplate;

    @MockitoBean
    RefreshTokenMapper refreshTokenMapper;

    @MockitoBean
    LoginAuditMapper loginAuditMapper;

    @MockitoBean
    FaqCategoryMapper faqCategoryMapper;

    @MockitoBean
    FaqItemMapper faqItemMapper;

    @MockitoBean
    ManualDocMapper manualDocMapper;

    @MockitoBean
    AdminAuthService adminAuthService;

    @MockitoBean
    AdminManagementService adminManagementService;

    @MockitoBean
    OperationAuditService operationAuditService;

    @MockitoBean
    ManualStorageService manualStorageService;

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }

}
