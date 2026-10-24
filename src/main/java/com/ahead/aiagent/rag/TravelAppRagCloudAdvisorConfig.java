package com.ahead.aiagent.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class TravelAppRagCloudAdvisorConfig {

    // 修复 E25/O21：加默认值，避免缺少 dashscope key 时应用启动失败
    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    @Bean
    public Advisor travelAppRagCloudAdvisor() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
        final String KNOWLEDGE_BASE_INDEX = "旅行大师";
        DocumentRetriever dashScopeDocumentRetrievalAdvisor = new DashScopeDocumentRetriever(dashScopeApi,
                DashScopeDocumentRetrieverOptions.builder()
                        .withIndexName(KNOWLEDGE_BASE_INDEX)
                        .build());
        return RetrievalAugmentationAdvisor.builder().documentRetriever(dashScopeDocumentRetrievalAdvisor).build();
    }
}