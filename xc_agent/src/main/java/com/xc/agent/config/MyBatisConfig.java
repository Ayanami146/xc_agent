package com.xc.agent.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
@MapperScan("com.xc.agent.mapper")
public class MyBatisConfig {
}
