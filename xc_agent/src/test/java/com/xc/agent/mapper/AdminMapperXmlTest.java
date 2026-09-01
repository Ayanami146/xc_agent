package com.xc.agent.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminMapperXmlTest {
    @Test
    void parsesAllAdminMapperXmlWithoutDatabase() throws Exception {
        Configuration configuration = new Configuration();
        for (String name : List.of("AdminUserMapper", "AdminDashboardMapper", "TicketMapper",
                "TicketReplyMapper", "TicketAttachmentMapper", "TicketStatusHistoryMapper",
                "FaqCategoryMapper", "FaqItemMapper", "ManualCategoryMapper", "ManualDocMapper",
                "OperationAuditMapper")) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertThat(input).as(resource).isNotNull();
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        assertThat(configuration.hasStatement("com.xc.agent.mapper.AdminUserMapper.selectByUsername")).isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.TicketMapper.transition")).isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.ManualDocMapper.replaceFile")).isTrue();
        String ragSql = configuration
                .getMappedStatement("com.xc.agent.mapper.ManualDocMapper.selectPublishedForRag")
                .getBoundSql(null).getSql().replaceAll("\\s+", " ");
        assertThat(ragSql)
                .contains("d.status='PUBLISHED'", "c.status='ENABLED'", "d.deleted_at IS NULL")
                .contains("d.object_key REGEXP", "d.sha256 REGEXP", "pdf|docx|txt|md")
                .doesNotContain("pdf|doc|");
    }
}
