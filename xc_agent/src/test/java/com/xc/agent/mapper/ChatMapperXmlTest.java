package com.xc.agent.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMapperXmlTest {
    @Test
    void parsesChatMapperXmlWithoutDatabase() throws Exception {
        Configuration configuration = new Configuration();
        for (String name : new String[]{
                "ChatSessionMapper", "ChatRequestMapper", "ChatMessageMapper",
                "MessageCitationMapper", "MessageFeedbackMapper"}) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                new XMLMapperBuilder(
                        input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        String namespace = "com.xc.agent.mapper.ChatSessionMapper.";
        assertThat(configuration.hasStatement(namespace + "list")).isTrue();
        assertThat(configuration.hasStatement(namespace + "count")).isTrue();
        assertThat(configuration.hasStatement(namespace + "selectByIdAndUserId")).isTrue();
        assertThat(configuration.hasStatement(namespace + "rename")).isTrue();
        assertThat(configuration.hasStatement(namespace + "softDelete")).isTrue();
        assertThat(configuration.hasStatement(namespace + "insert")).isTrue();
        assertThat(configuration.hasStatement(namespace + "updatePreview")).isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.ChatMessageMapper.list")).isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.ChatMessageMapper.count")).isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.ChatMessageMapper.insert")).isTrue();
        assertThat(configuration.hasStatement(
                "com.xc.agent.mapper.ChatMessageMapper.updateCompleted")).isTrue();
        assertThat(configuration.hasStatement(
                "com.xc.agent.mapper.ChatRequestMapper.markSucceeded")).isTrue();
        assertThat(configuration.hasStatement(
                "com.xc.agent.mapper.ChatRequestMapper.markCancelled")).isTrue();
        assertThat(configuration.hasStatement(
                "com.xc.agent.mapper.MessageCitationMapper.insertBatch")).isTrue();
        assertThat(configuration.hasStatement(
                "com.xc.agent.mapper.MessageFeedbackMapper.upsert")).isTrue();
    }
}
