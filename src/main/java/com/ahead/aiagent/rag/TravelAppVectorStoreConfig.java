package com.ahead.aiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 旅行大师向量数据库配置（基于初始化内存的向量数据库Bean）并保存文档
 */

@Configuration
public class TravelAppVectorStoreConfig {

    @Resource
    private TravelAppDocumentLoader travelAppDocumentLoader;

    //引入自定义切词器
    @Resource
    private MyTokenTextSplitter myTokenTextSplitter;

    @Resource
    private MyKeywordEnricher myKeywordEnricher;
    @Bean
    VectorStore travelAppVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore =SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        //加载文档
        List<Document> documentList=travelAppDocumentLoader.loadMarkdowns();
        //自主切分文档--不建议采取自主切分，建议使用自动切分的方式
        //List< Document> splitDocuments = myTokenTextSplitter.splitCustomized(documentList);
        //List<Document> splitDocuments = myTokenTextSplitter.splitDocuments(documentList);
        List<Document> enrichedDocuments = myKeywordEnricher.enrichDocuments(documentList);
        //simpleVectorStore.add(documentList);
        //simpleVectorStore.add(splitDocuments);
        simpleVectorStore.add(enrichedDocuments);
        return simpleVectorStore;
    }

}
