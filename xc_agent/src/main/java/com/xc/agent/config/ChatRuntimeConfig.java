package com.xc.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 聊天链路运行时依赖。
 *
 * <p>Agent 的 SSE 响应需要阻塞读取较长时间。Java 21 虚拟线程适合这种 I/O 场景，
 * 可以避免占住 Tomcat 请求线程，同时不必为了一个内部 HTTP 调用引入响应式框架。</p>
 */
@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class ChatRuntimeConfig {

    @Bean
    public HttpClient agentHttpClient(AgentProperties properties) {
        return HttpClient.newBuilder()
                // Uvicorn 当前只处理 HTTP/1.1。JDK HttpClient 默认会向明文地址发送
                // HTTP/2 h2c Upgrade，Uvicorn 会把它识别为不支持的升级请求，并可能在
                // 后续请求解析中返回 422。内部 SSE 链路无需 HTTP/2，固定 1.1 更稳定。
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    /**
     * 每次聊天流占用一个虚拟线程；应用关闭时由 Spring 调用 close 等待任务收尾。
     */
    @Bean(name = "chatExecutor", destroyMethod = "close")
    public ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
