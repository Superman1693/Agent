package com.ahead.aiagent.demo.rag;



import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 查询扩展器Demo---根据你的查询问题进行拓展，产生多个相关问题
 */
@Component
public class MultiQueryExpanderDemo {


    private final ChatClient.Builder chatClientBuilder;

    public MultiQueryExpanderDemo(ChatModel dashScopeChatModel) {
        this.chatClientBuilder =ChatClient.builder(dashScopeChatModel);
    }

    public List<Query> expand(String query) {
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .build();
        List<Query> queries = queryExpander.expand(new Query("谁是峰古啊？"));
        return queries;

    }

}
