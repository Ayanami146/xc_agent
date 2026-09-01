package com.xc.agent.controller;

import com.xc.agent.common.exception.BusinessException;
import com.xc.agent.config.AgentProperties;
import com.xc.agent.service.content.ManualRagManifestService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalAiControllerTest {

    @Test
    void rejectsMismatchedInternalToken() {
        ManualRagManifestService service = mock(ManualRagManifestService.class);
        InternalAiController controller = new InternalAiController(service, properties("expected"));

        assertThatThrownBy(() -> controller.manuals("wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INTERNAL_AUTH_INVALID");
    }

    @Test
    void acceptsMatchingInternalToken() {
        ManualRagManifestService service = mock(ManualRagManifestService.class);
        InternalAiController controller = new InternalAiController(service, properties("expected"));

        controller.manuals("expected");

        verify(service).listPublishedManuals();
    }

    private AgentProperties properties(String token) {
        return new AgentProperties("http://127.0.0.1:8100/internal/ai/v1", token,
                Duration.ofSeconds(5), Duration.ofSeconds(130), Duration.ofSeconds(135),
                "default", List.of("default"), 1024);
    }
}
