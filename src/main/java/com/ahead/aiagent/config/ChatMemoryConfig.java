package com.ahead.aiagent.config;

import com.ahead.aiagent.chatmemory.FileBasedChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 功能8：会话记忆配置。
 * FileBasedChatMemory 原先只有测试/演示用法（手动 new），Manus 多轮历史上下文
 * 需要跨请求共享同一份会话存储，这里注册为单例 Bean，目录沿用项目 tmp 约定。
 */
@Configuration
public class ChatMemoryConfig {

    @Bean
    public FileBasedChatMemory fileBasedChatMemory() {
        // 会话历史文件目录：{user.dir}/tmp/chat-memory，与 FileConstant.FILE_SAVE_DIR 的 tmp 约定一致
        return new FileBasedChatMemory(System.getProperty("user.dir") + "/tmp/chat-memory");
    }
}
