package com.xc.agent.service.impl;

import com.xc.agent.common.security.AuthContext;
import com.xc.agent.common.security.AuthPrincipal;
import com.xc.agent.common.util.CryptoService;
import com.xc.agent.config.AgentProperties;
import com.xc.agent.mapper.ChatMessageMapper;
import com.xc.agent.mapper.ChatRequestMapper;
import com.xc.agent.mapper.ChatSessionMapper;
import com.xc.agent.mapper.MessageCitationMapper;
import com.xc.agent.mapper.MessageFeedbackMapper;
import com.xc.agent.model.dto.chat.ChatDTOs;
import com.xc.agent.model.enums.AuthEnums;
import com.xc.agent.model.enums.ChatEnums;
import com.xc.agent.model.po.ChatMessagePO;
import com.xc.agent.model.po.ChatRequestPO;
import com.xc.agent.model.po.ChatSessionPO;
import com.xc.agent.model.vo.chat.ChatVOs;
import com.xc.agent.service.InternalAiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ChatService 的核心状态流测试；Mapper 使用 mock，不依赖开发者 MySQL。 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {
    @Mock ChatSessionMapper chatSessionMapper;
    @Mock ChatRequestMapper chatRequestMapper;
    @Mock ChatMessageMapper chatMessageMapper;
    @Mock MessageCitationMapper messageCitationMapper;
    @Mock MessageFeedbackMapper messageFeedbackMapper;
    @Mock InternalAiService internalAiService;
    @Mock PlatformTransactionManager transactionManager;

    private ChatServiceImpl service;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenAnswer(ignored -> new SimpleTransactionStatus());
        executor = Executors.newSingleThreadExecutor();
        AgentProperties properties = new AgentProperties(
                "http://127.0.0.1:8100/internal/ai/v1", "",
                Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(6),
                "default", List.of("default"), 1024);
        service = new ChatServiceImpl(
                chatSessionMapper, chatRequestMapper, chatMessageMapper,
                messageCitationMapper, messageFeedbackMapper, internalAiService,
                new CryptoService(new SecureRandom()), properties,
                new TransactionTemplate(transactionManager), executor);
        AuthContext.set(new AuthPrincipal(7L, "usr_test", AuthEnums.SubjectType.CUSTOMER));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
        executor.close();
    }

    @Test
    void createsRecordsCallsAgentAndCommitsSuccessfulAnswer() throws Exception {
        AtomicLong messageId = new AtomicLong(30);
        doAnswer(invocation -> {
            ChatSessionPO session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        }).when(chatSessionMapper).insert(any(ChatSessionPO.class));
        doAnswer(invocation -> {
            ChatRequestPO request = invocation.getArgument(0);
            request.setId(20L);
            return 1;
        }).when(chatRequestMapper).insert(any(ChatRequestPO.class));
        doAnswer(invocation -> {
            ChatMessagePO message = invocation.getArgument(0);
            message.setId(messageId.getAndIncrement());
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessagePO.class));
        when(chatRequestMapper.markRunning(20L, 7L)).thenReturn(1);
        when(chatRequestMapper.markSucceeded(20L, 7L, "default", null)).thenReturn(1);
        when(chatMessageMapper.updateCompleted(31L, "完整回答", null, null)).thenReturn(1);

        ObjectMapper mapper = new ObjectMapper();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<InternalAiService.AgentEvent> consumer = invocation.getArgument(1);
            consumer.accept(new InternalAiService.AgentEvent(
                    "delta", mapper.readTree("{\"content\":\"完整回答\"}")));
            consumer.accept(new InternalAiService.AgentEvent(
                    "done", mapper.readTree("{\"finishReason\":\"stop\"}")));
            return null;
        }).when(internalAiService).stream(any(), any());

        service.startStream(new ChatDTOs.ChatStreamDTO(null, "如何安装驱动？"));

        ArgumentCaptor<com.xc.agent.model.dto.internal.InternalAiDTOs.AgentChatStreamDTO> request =
                ArgumentCaptor.forClass(
                        com.xc.agent.model.dto.internal.InternalAiDTOs.AgentChatStreamDTO.class);
        verify(internalAiService, org.mockito.Mockito.timeout(2000))
                .stream(request.capture(), any());
        verify(chatMessageMapper, org.mockito.Mockito.timeout(2000))
                .updateCompleted(31L, "完整回答", null, null);
        assertThat(request.getValue().requestId()).isEqualTo(20L);
        assertThat(request.getValue().sessionId()).isEqualTo(10L);
        assertThat(request.getValue().userId()).isEqualTo(7L);
        assertThat(request.getValue().history()).isNull();
        assertThat(request.getValue().policy().knowledgeBaseIds()).containsExactly("default");
    }

    @Test
    void cancelsActiveRequestAndPropagatesToAgent() {
        ChatRequestPO running = ChatRequestPO.builder()
                .id(20L).sessionId(10L).userId(7L).status("RUNNING").build();
        ChatRequestPO cancelled = ChatRequestPO.builder()
                .id(20L).sessionId(10L).userId(7L).status("CANCELLED")
                .errorCode("RUN_CANCELLED").errorMessage("本次回答已停止").build();
        when(chatRequestMapper.selectByIdAndUserId(20L, 7L))
                .thenReturn(running, cancelled);
        when(chatRequestMapper.markCancelled(20L, 7L)).thenReturn(1);
        when(chatMessageMapper.selectAssistantByRequestIdAndUserId(20L, 7L))
                .thenReturn(ChatMessagePO.builder()
                        .id(31L).requestId(20L).content("").status("INTERRUPTED").build());
        when(messageCitationMapper.selectByMessageIds(List.of(31L))).thenReturn(List.of());

        ChatVOs.ChatRequestResultVO result = service.cancelRequest(20L);

        assertThat(result.status()).isEqualTo(ChatEnums.RequestStatus.CANCELLED);
        verify(chatRequestMapper).markCancelled(20L, 7L);
        verify(chatMessageMapper).updateInterruptedByRequestId(20L, 7L);
        verify(internalAiService).cancel(20L);
    }

    @Test
    void terminalRequestCancellationIsIdempotent() {
        ChatRequestPO succeeded = ChatRequestPO.builder()
                .id(20L).sessionId(10L).userId(7L).status("SUCCEEDED").build();
        when(chatRequestMapper.selectByIdAndUserId(20L, 7L)).thenReturn(succeeded);
        when(chatMessageMapper.selectAssistantByRequestIdAndUserId(20L, 7L))
                .thenReturn(ChatMessagePO.builder()
                        .id(31L).requestId(20L).content("完成").status("COMPLETED").build());
        when(messageCitationMapper.selectByMessageIds(List.of(31L))).thenReturn(List.of());

        ChatVOs.ChatRequestResultVO result = service.cancelRequest(20L);

        assertThat(result.status()).isEqualTo(ChatEnums.RequestStatus.SUCCEEDED);
        verify(chatRequestMapper, never()).markCancelled(any(), any());
        verify(internalAiService, never()).cancel(any());
    }

    @Test
    void savesAndClearsOwnedMessageFeedback() {
        when(chatMessageMapper.selectOwnedAssistantById(31L, 7L))
                .thenReturn(ChatMessagePO.builder().id(31L).role("assistant").build());

        service.saveFeedback(31L, new ChatDTOs.MessageFeedbackDTO(ChatEnums.Feedback.up));
        service.saveFeedback(31L, new ChatDTOs.MessageFeedbackDTO(null));

        verify(messageFeedbackMapper).upsert(31L, 7L, "up");
        verify(messageFeedbackMapper).delete(31L, 7L);
    }
}
