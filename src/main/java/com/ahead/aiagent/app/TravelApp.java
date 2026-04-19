package com.ahead.aiagent.app;

import com.ahead.aiagent.advisor.MySimpleLoggerAdvisor;
import com.ahead.aiagent.advisor.ReReadingAdvisor;
import com.ahead.aiagent.demo.rag.QueryRewriter;
import com.ahead.aiagent.rag.TravelAppRagCustomAdvisorFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;

import org.springframework.ai.chat.client.advisor.api.Advisor;
// 引入新的包路径
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory; // 用于获取 CONVERSATION_ID
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

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
                                // 不管用户输入什么，都将忽略，将下面提示词输出给AI
                                // new ReReadingAdvisor("请根据我的需求，生成一个完整的旅游方案，并详细说明每个步骤。"))
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

        @Resource
        private VectorStore travelAppVectorStore;

        @Resource
        private Advisor travelAppRagCloudAdvisor;

        @Resource
        private VectorStore pgVectorVectorStore;

        @Resource
        private QueryRewriter queryRewriter;

        @Resource
        private MySimpleLoggerAdvisor mySimpleLoggerAdvisor;

        public String doChatWithRag(String message, String chatId) {
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
                String augmentedMessage = "参考资料：\n" + context + "\n根据以上参考资料，回答问题：" + rewriteMessage;

                // RAG场景用chatModel重新构建干净的client，不含ReReadingAdvisor（它会覆盖user message）
                ChatMemory ragMemory = MessageWindowChatMemory.builder().maxMessages(20).build();

                ChatClient ragChatClient = ChatClient.builder(chatModel)
                                .defaultSystem(SYSTEM_PROMPT)
                                .defaultAdvisors(
                                                MessageChatMemoryAdvisor.builder(ragMemory).build(),
                                                mySimpleLoggerAdvisor,
                                                // 过滤特定状态的文档
                                                TravelAppRagCustomAdvisorFactory.createTravelAppRAgCustomAdvisor(
                                                                travelAppVectorStore, "生活")
                                // 旧版样式
                                // 开启日志，便于观察效果
                                // .advisors(new MySimpleLoggerAdvisor())
                                // 注意：由于新版强制使用 Builder 模式，如果你后续放开下面这行注释，也需写成 builder()
                                // 应用RAG知识库问答
                                // QuestionAnswerAdvisor.builder(travelAppVectorStore).build()
                                // 应用RAG索引增强服务
                                // travelAppRagCloudAdvisor
                                // 应用RAG索引增强服务（基于pgVector向量存储）
                                // QuestionAnswerAdvisor.builder(pgVectorVectorStore).build()
                                )
                                .build();

                ChatResponse chatResponse = ragChatClient
                                .prompt()
                                .user(augmentedMessage)
                                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                                .call()
                                .chatResponse();
                String content = chatResponse.getResult().getOutput().getText();
                log.info("content:{}", content);
                return content;
        }

        @Resource
        private ToolCallback[] allTools;

        public String doChatWithTools(String message, String chatId) {
                // 1. 基于共享的 toolChatMemory 创建记忆 Advisor
                MessageChatMemoryAdvisor toolMemoryAdvisor = MessageChatMemoryAdvisor.builder(toolChatMemory).build();
                // 2. 构建干净的 ChatClient
                ChatClient toolChatClient = ChatClient.builder(chatModel)
                                .defaultSystem("你是一个智能助手，请根据用户的指令，灵活使用提供的工具（如文件读写、终端执行等）来完成任务。如果不需要工具则直接回答。")
                                .defaultAdvisors(
                                                toolMemoryAdvisor, // ✅ 注入记忆 Advisor
                                                new MySimpleLoggerAdvisor())
                                .build();

                // 3. 执行调用
                ChatResponse response = toolChatClient
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

        // AI调用mcp服务
        @Resource
        private ToolCallbackProvider toolCallbackProvider;

        public String doChatWithMcp(String message, String chatId) {
                // 1. 基于共享的 toolChatMemory 创建记忆 Advisor
                MessageChatMemoryAdvisor toolMemoryAdvisor = MessageChatMemoryAdvisor.builder(toolChatMemory).build();
                // 2. 构建干净的 ChatClient
                ChatClient toolChatClient = ChatClient.builder(chatModel)
                                .defaultSystem("你是一个智能助手，请根据用户的指令，灵活使用提供的工具（如文件读写、终端执行等）来完成任务。如果不需要工具则直接回答。")
                                .defaultAdvisors(
                                                toolMemoryAdvisor, // ✅ 注入记忆 Advisor
                                                new MySimpleLoggerAdvisor())
                                .build();

                // 3. 执行调用
                ChatResponse response = toolChatClient
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

}
