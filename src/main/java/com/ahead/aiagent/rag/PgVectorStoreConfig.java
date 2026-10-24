package com.ahead.aiagent.rag;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

/**
 * 自定义配置向量库
 * 使用独立的 PostgreSQL 数据源用于向量存储
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.pgvector", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PgVectorStoreConfig {

  @Resource
  private TravelAppDocumentLoader travelAppDocumentLoader;

  /** 修复 E9/O12（二次修正）：向量维度以 yml 为单一来源；默认值同步为 1024（text-embedding-v3 支持上限） */
  @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}")
  private int vectorDimensions;

  /**
   * PGVector 专用数据源
   */
  @Bean(name = "pgVectorDataSource")
  @ConfigurationProperties(prefix = "spring.ai.vectorstore.pgvector.datasource")
  public DataSource pgVectorDataSource() {
    return DataSourceBuilder.create().type(HikariDataSource.class).build();
  }

  /**
   * PGVector 专用 JdbcTemplate
   */
  @Bean(name = "pgVectorJdbcTemplate")
  public JdbcTemplate pgVectorJdbcTemplate(@Qualifier("pgVectorDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean("pgVectorVectorStore")
  @Primary
  public VectorStore pgVectorVectorStore(
      @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
      EmbeddingModel dashscopeEmbeddingModel) {

    PgVectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
        .dimensions(vectorDimensions)
        .distanceType(COSINE_DISTANCE)
        .indexType(HNSW)
        .initializeSchema(false)
        .schemaName("public")
        .vectorTableName("vector_store")
        .maxDocumentBatchSize(10) // 修复：DashScope text-embedding-v3 单次批量上限为 10（原 10000 会触发 400）
        .build();
    // 加载数据
    List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT COUNT(*) as cnt FROM public.vector_store");
    long count = ((Number) result.get(0).get("cnt")).longValue();
    if (count == 0) {
      List<Document> documents = travelAppDocumentLoader.loadMarkdowns();
      // 修复：DashScope text-embedding-v3 单次批量上限为 10（input.contents），
      // 而 PgVectorStore.add 会把整批文档一次性交给 EmbeddingModel（maxDocumentBatchSize 只控制入库分批），
      // 因此必须在调用侧自行分批，否则启动即报 400 InvalidParameter: batch size is invalid。
      int embeddingBatchSize = 10;
      for (int i = 0; i < documents.size(); i += embeddingBatchSize) {
        int end = Math.min(i + embeddingBatchSize, documents.size());
        vectorStore.add(documents.subList(i, end));
      }
      log.info("向量库初始化完成：共写入 {} 条文档（每批 {} 条）", documents.size(), embeddingBatchSize);
    }
    return vectorStore;
  }

}
