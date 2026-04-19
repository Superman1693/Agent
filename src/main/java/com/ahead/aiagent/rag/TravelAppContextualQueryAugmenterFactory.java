package com.ahead.aiagent.rag;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

/**
 * 创建上下文查询增强工厂
 */
public class TravelAppContextualQueryAugmenterFactory {

    /**
     * 创建上下文查询增强--知识库中查询不到相关内容就返回相关提示词
     * @return  上下文查询增强
     */
    public static ContextualQueryAugmenter createInstance(){
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                你应该输出下面内容：
                抱歉，我只能回答旅游相关的问题，别的没办法帮到您，
                有什么问题可以联系客服https://r-ren.icu
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }
}
