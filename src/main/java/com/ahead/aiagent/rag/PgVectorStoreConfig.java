package com.ahead.aiagent.rag;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
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
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.pgvector", name = "enabled", havingValue = "true", matchIfMissing = false)
public class PgVectorStoreConfig {

  @Resource
  private TravelAppDocumentLoader travelAppDocumentLoader;

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
        .dimensions(1536)
        .distanceType(COSINE_DISTANCE)
        .indexType(HNSW)
        .initializeSchema(false)
        .schemaName("public")
        .vectorTableName("vector_store")
        .maxDocumentBatchSize(10000) // 最大批量插入文件数
        .build();
    // 加载数据
    List<Map<String, Object>> result = jdbcTemplate.queryForList("SELECT COUNT(*) as cnt FROM public.vector_store");
    long count = ((Number) result.get(0).get("cnt")).longValue();
    if (count == 0) {
      List<Document> documents = travelAppDocumentLoader.loadMarkdowns();
      vectorStore.add(documents);
    }
    return vectorStore;
  }

}
