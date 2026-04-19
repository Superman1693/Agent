package com.ahead.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.ahead.aiagent.constant.FileConstant;
import com.ahead.aiagent.service.FileArtifactService;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

public class PDFGenerationTool {

    private final FileArtifactService fileArtifactService;

    public PDFGenerationTool(FileArtifactService fileArtifactService) {
        this.fileArtifactService = fileArtifactService;
    }

    //returnDirect = true将生成的PDF文件直接返回给用户
    @Tool(description = "Generate a PDF file with given content",returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String normalizedFileName = normalizePdfFileName(fileName);
        String sanitizedContent = sanitizePdfContent(content);
        String filePath = fileDir + "/" + normalizedFileName;
        try {

            FileUtil.mkdir(fileDir);

            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                //使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);
                //创建段落
                Paragraph paragraph = new Paragraph(sanitizedContent);
                //添加段落并关闭文档
                document.add(paragraph);
            }
            String artifactId = fileArtifactService.registerPdfArtifact(filePath, normalizedFileName, null);
            return "PDF 生成成功。artifactId=" + artifactId + "; fileName=" + normalizedFileName;
        } catch (IOException e) {
            return "PDF 生成失败：" + e.getMessage();
        }
    }

    private String normalizePdfFileName(String fileName) {
        String candidate = (fileName == null || fileName.isBlank()) ? "travel_plan.pdf" : fileName.trim();
        candidate = candidate.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!candidate.toLowerCase().endsWith(".pdf")) {
            candidate = candidate + ".pdf";
        }
        return candidate;
    }

    private String sanitizePdfContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(content.length());
        content.codePoints().forEach(cp -> {
            if (cp <= 0xFFFF) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }
}