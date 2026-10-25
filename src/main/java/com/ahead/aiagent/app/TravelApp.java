package com.ahead.aiagent.app;

import com.ahead.aiagent.advisor.MySimpleLoggerAdvisor;
import com.ahead.aiagent.demo.rag.QueryRewriter;
import com.ahead.aiagent.rag.TravelAppRagCustomAdvisorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory; // 用于获取 CONVERSATION_ID
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class TravelApp {

        private static final String SYSTEM_PROMPT = "你是专业旅游顾问，请根据用户提供的参考资料回答问题，" +
                        "直接给出准确、简洁、实用的答案。";

        private final ChatClient chatClient;
        // 保存ChatModel引用，用于RAG场景构建不含ReReadingAdvisor的干净client
        private final ChatModel chatModel;
        private final ChatMemory toolChatMemory;

        // 修复 O7：ChatClient 懒加载缓存，避免每次调用都 ChatClient.builder(...).build()
        private volatile ChatClient ragChatClient;
        private volatile ChatClient toolChatClient;
        private volatile ChatClient mcpChatClient;

        public TravelApp(ChatModel dashscopeChatModel) {
                this.chatModel = dashscopeChatModel;
                ChatMemory chatMemory = MessageWindowChatMemory.builder()
                                .maxMessages(20)
                                .build();
                MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
                this.toolChatMemory = MessageWindowChatMemory.builder().maxMessages(20).build();
                this.chatClient = ChatClient.builder(dashscopeChatModel)
                                .defaultSystem(SYSTEM_PROMPT)
                                .defaultAdvisors(
                                                memoryAdvisor,
                                                new MySimpleLoggerAdvisor())
                                .build();
        }

        public String doChat(String message, String chatId) {
                ChatResponse chatResponse = chatClient
                                .prompt()
                                .user(message)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .call()
                                .chatResponse();

                String content = chatResponse.getResult().getOutput().getText();
                log.info("content:{}", content);
                return content;
        }

        /**
         * AI基础对话，SSE流失传世
         *
         * @param message
         * @param chatId
         * @return
         */
        public Flux<String> doChatByStream(String message, String chatId) {
                return chatClient
                                .prompt()
                                .user(message)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .stream()
                                .content();
        }

        public record TravelReport(String title, List<String> suggestions) {
        }

        public TravelReport doChatWithReport(String message, String chatId) {
                TravelReport travelReport = chatClient
                                .prompt()
                                .system(SYSTEM_PROMPT + "请将我的需求转化为旅游报告，并详细说明每个步骤。")
                                .user(message)
                                // 移除 CHAT_MEMORY_RETRIEVE_SIZE_KEY 参数，保留 CONVERSATION_ID
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .call()
                                .entity(TravelReport.class);

                log.info("travelReport:{}", travelReport);
                return travelReport;
        }

        // 修复 E25/O21：非核心能力改为可选注入，任一外部依赖缺失不再导致应用启动失败
        @Autowired(required = false)
        private VectorStore travelAppVectorStore;

        @Autowired(required = false)
        private Advisor travelAppRagCloudAdvisor;

        @Autowired(required = false)
        private VectorStore pgVectorVectorStore;

        @Autowired(required = false)
        private QueryRewriter queryRewriter;

        @Autowired(required = false)
        private MySimpleLoggerAdvisor mySimpleLoggerAdvisor;

        public String doChatWithRag(String message, String chatId) {
                String augmentedMessage = buildRagAugmentedMessage(message);
                ChatResponse chatResponse = getRagChatClient()
                                .prompt()
                                .user(augmentedMessage)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .call()
                                .chatResponse();
                String content = chatResponse.getResult().getOutput().getText();
                log.info("content:{}", content);
                return content;
        }

        /**
         * RAG 流式对话：QueryRewriter 重写 → 向量检索 → 拼接上下文 → .stream().content() 逐段输出。
         * 复用懒加载缓存的干净 RAG ChatClient（无 ReReadingAdvisor），链路与 doChatWithRag 完全一致。
         */
        public Flux<String> doChatWithRagByStream(String message, String chatId) {
                String augmentedMessage = buildRagAugmentedMessage(message);
                return getRagChatClient()
                                .prompt()
                                .user(augmentedMessage)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .stream()
                                .content();
        }

        /**
         * RAG 前置链路：查询重写 + 向量检索 + 手动拼接参考资料上下文（同步/流式共用）。
         * 依赖缺失时抛出与 doChatWithRag 一致的异常。
         */
        private String buildRagAugmentedMessage(String message) {
                if (pgVectorVectorStore == null || queryRewriter == null || travelAppVectorStore == null) {
                        throw new IllegalStateException("RAG 能力未启用：请配置向量库与查询重写器");
                }
                // 查询重写
                String rewriteMessage = queryRewriter.doQueryRewrite(message);
                // 诊断：直接测试向量检索
                List<org.springframework.ai.document.Document> docs = pgVectorVectorStore.similaritySearch(
                                org.springframework.ai.vectorstore.SearchRequest.builder()
                                                .query(rewriteMessage).topK(4).build());
                log.info("RAG检索到文档数量: {}", docs.size());
                docs.forEach(d -> log.info("检索文档片段: {}",
                                d.getText().substring(0, Math.min(100, d.getText().length()))));

                // 手动拼接RAG上下文直接注入user message
                StringBuilder context = new StringBuilder();
                docs.forEach(d -> context.append(d.getText()).append("\n\n"));
                return "参考资料：\n" + context + "\n根据以上参考资料，回答问题：" + rewriteMessage;
        }

        private ChatClient getRagChatClient() {
                ChatClient local = ragChatClient;
                if (local == null) {
                        synchronized (this) {
                                local = ragChatClient;
                                if (local == null) {
                                        ChatMemory ragMemory = MessageWindowChatMemory.builder().maxMessages(20).build();
                                        local = ChatClient.builder(chatModel)
                                                        .defaultSystem(SYSTEM_PROMPT)
                                                        .defaultAdvisors(
                                                                        MessageChatMemoryAdvisor.builder(ragMemory).build(),
                                                                        mySimpleLoggerAdvisor == null ? new MySimpleLoggerAdvisor() : mySimpleLoggerAdvisor,
                                                                        TravelAppRagCustomAdvisorFactory.createTravelAppRAgCustomAdvisor(
                                                                                        travelAppVectorStore, "生活"))
                                                        .build();
                                        ragChatClient = local;
                                }
                        }
                }
                return local;
        }

        // 修复 E25/O21：工具能力改为可选注入
        @Autowired(required = false)
        private ToolCallback[] allTools;

        public String doChatWithTools(String message, String chatId) {
                if (allTools == null || allTools.length == 0) {
                        throw new IllegalStateException("工具能力未启用：未注册任何 ToolCallback");
                }
                ChatResponse response = getToolChatClient()
                                .prompt()
                                .user(message)
                                // 传入 chatId，告诉 Advisor 去 toolChatMemory 的哪个会话桶里读写上下文
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .toolCallbacks(allTools)
                                .call()
                                .chatResponse();

                String content = response.getResult().getOutput().getText();
                log.info("content: {}", content);
                return content;
        }

        private ChatClient getToolChatClient() {
                ChatClient local = toolChatClient;
                if (local == null) {
                        synchronized (this) {
                                local = toolChatClient;
                                if (local == null) {
                                        local = buildToolClient();
                                        toolChatClient = local;
                                }
                        }
                }
                return local;
        }

        private ChatClient buildToolClient() {
                MessageChatMemoryAdvisor toolMemoryAdvisor = MessageChatMemoryAdvisor.builder(toolChatMemory).build();
                return ChatClient.builder(chatModel)
                                .defaultSystem("你是一个智能助手，请根据用户的指令，灵活使用提供的工具（如文件读写、终端执行等）来完成任务。如果不需要工具则直接回答。")
                                .defaultAdvisors(
                                                toolMemoryAdvisor,
                                                new MySimpleLoggerAdvisor())
                                .build();
        }

        // AI调用mcp服务
        // 修复 E25/O21：MCP 客户端改为可选注入，MCP Server 未启动不再导致启动失败
        @Autowired(required = false)
        private ToolCallbackProvider toolCallbackProvider;

        public String doChatWithMcp(String message, String chatId) {
                if (toolCallbackProvider == null) {
                        throw new IllegalStateException("MCP 能力未启用：未检测到 ToolCallbackProvider");
                }
                ChatResponse response = getMcpChatClient()
                                .prompt()
                                .user(message)
                                // 传入 chatId，告诉 Advisor 去 toolChatMemory 的哪个会话桶里读写上下文
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .toolCallbacks(toolCallbackProvider)
                                .call()
                                .chatResponse();

                String content = response.getResult().getOutput().getText();
                log.info("content: {}", content);
                return content;
        }

        private ChatClient getMcpChatClient() {
                ChatClient local = mcpChatClient;
                if (local == null) {
                        synchronized (this) {
                                local = mcpChatClient;
                                if (local == null) {
                                        local = buildToolClient();
                                        mcpChatClient = local;
                                }
                        }
                }
                return local;
        }

}
