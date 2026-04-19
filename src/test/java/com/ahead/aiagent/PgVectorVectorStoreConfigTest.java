package com.ahead.aiagent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class PgVectorVectorStoreConfigTest {

    @Resource(name = "pgVectorVectorStore")
    VectorStore pgVectorVectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Test
    void test2() {
        System.out.println("当前模型维度：" + embeddingModel.dimensions());
        // 其他测试代码...
    }
    @Test
    void test() {
        System.out.println("==================== 测试方法开始执行 ====================");
        List<Document> documents = List.of(
                new Document("峰古在学agent", Map.of("meta1", "meta1")),
                new Document("但是学不懂啊，好难弄啊"),
                new Document("峰古太蠢了", Map.of("meta2", "meta2")));

        pgVectorVectorStore.add(documents);

        List<Document> results = pgVectorVectorStore.similaritySearch(SearchRequest.builder().query("峰古在干什么？").topK(3).build());
        Assertions.assertNotNull(results);
        System.out.println("==================== 执行成功 ====================");
    }
}