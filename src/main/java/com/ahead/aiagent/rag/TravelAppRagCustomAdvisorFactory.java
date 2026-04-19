package com.ahead.aiagent.rag;


import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * 创建自定义的RAG检索增强顾问的工厂
 */
@Component
public class TravelAppRagCustomAdvisorFactory {
    /**
     * 创建自定义的RAG检索增强顾问
     * @param vectorStore  向量存储
     * @param status    状态
     * @return  自定义的RAG检索增强顾问
     */
    public static Advisor createTravelAppRAgCustomAdvisor(VectorStore vectorStore,String status) {
        //过滤特定状态文档
        Filter.Expression expression =new FilterExpressionBuilder()
                .eq("status", status)
                .build();
        DocumentRetriever documentRetriever= VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)//过滤条件
                .similarityThreshold(0.7)   //相似度阈值
                .topK(3)    //返回文档数量
                .build();



        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(documentRetriever)
                //使用自定义的上下文查询增强工厂
                        .queryAugmenter(TravelAppContextualQueryAugmenterFactory.createInstance())
                        .build();
    }

}
