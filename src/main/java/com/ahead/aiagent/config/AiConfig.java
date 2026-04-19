package com.ahead.aiagent.config;

import com.ahead.aiagent.advisor.MySimpleLoggerAdvisor;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Bean
    public MySimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new MySimpleLoggerAdvisor();
    }


    // 设置MCP超时时间,默认为20秒极有可能出现超时无法连接mcp客户端情况
    @Bean
    public McpSyncClientCustomizer mcpTimeoutCustomizer() {
        return (name, spec) -> spec.requestTimeout(Duration.ofSeconds(60));
    }
}
