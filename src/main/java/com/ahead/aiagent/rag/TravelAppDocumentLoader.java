package com.ahead.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TravelAppDocumentLoader {

    private final ResourcePatternResolver resourcePatternResolver;

    public TravelAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /*
     * 加载所有markdown文档，并转换为document列表
     */
    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath*:document/*.md");
            for (Resource resource : resources) {
                String fileName = resource.getFilename();
                //提取文档的名称部分内容作为标签
                String status=fileName.substring(9, fileName.length()-5);
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", fileName)//将文件名称作为元数据
                        .withAdditionalMetadata("status", status)
                        .build();

                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);

                allDocuments.addAll(reader.read().stream()
                        // 过滤掉纯metadata块（内容以"metadata:"开头，不含实际问答内容）
                        .filter(doc -> {
                            String text = doc.getText().trim();
                            return !text.startsWith("metadata:") && text.length() > 10;
                        })
                        .toList());
            }
        } catch (IOException e) {
            log.error("Markdown文档加载失败", e);
        }
        return allDocuments;
    }
}