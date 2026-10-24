package com.ahead.aiagent.config;

import com.ahead.aiagent.advisor.MySimpleLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 修复 O6：Agent 使用的 ChatClient 改为单例复用。
 *
 * <p>此前 {@code Manus} 构造函数每次执行 {@code ChatClient.builder(...).build()}，
 * 而 Manus 又是「每请求 new」，导致每个请求都重复构建一次 ChatClient（含 advisor 链）。
 * 这里把它提升为 Spring Bean，Agent 实例共享同一个不可变客户端。</p>
 *
 * <p>说明：Agent 的会话状态（state / messageList / currentStep）仍保留为实例字段，
 * 由「每请求 new Agent」保证线程安全；若要进一步支持水平扩展，可按清单建议把
 * 可变状态抽成独立的 AgentSession（或存 Redis），Agent 本体做成无状态单例。</p>
 */
@Configuration
public class AgentChatClientConfig {

    @Bean(name = "manusChatClient")
    public ChatClient manusChatClient(ChatModel dashscopeChatModel) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MySimpleLoggerAdvisor())
                .build();
    }
}
