package com.xc.agent.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AuthMapperXmlTest {

    @Test
    void parsesAuthenticationMapperXmlWithoutDatabase() throws IOException {
        Configuration configuration = new Configuration();

        parse(configuration, "mapper/CustomerUserMapper.xml");
        parse(configuration, "mapper/RefreshTokenMapper.xml");
        parse(configuration, "mapper/LoginAuditMapper.xml");

        assertThat(configuration.hasStatement("com.xc.agent.mapper.CustomerUserMapper.selectByAccount"))
                .isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.RefreshTokenMapper.rotate"))
                .isTrue();
        assertThat(configuration.hasStatement("com.xc.agent.mapper.LoginAuditMapper.insert"))
                .isTrue();
    }

    private void parse(Configuration configuration, String resource) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
    }
}
