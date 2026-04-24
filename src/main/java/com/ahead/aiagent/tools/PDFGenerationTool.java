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
import java.nio.charset.StandardCharsets;

public class PDFGenerationTool {

    private static final float DEFAULT_FONT_SIZE = 11f;

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
        String filePath = fileDir + "/" + normalizedFileName;
        try {

            FileUtil.mkdir(fileDir);

            String sanitizedContent;
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                //使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                sanitizedContent = sanitizePdfContent(content, font);
                document.setFont(font);
                document.setFontSize(DEFAULT_FONT_SIZE);

                // 分行写入，避免整段文本因个别字符问题导致版式计算异常
                String[] lines = sanitizedContent.split("\\n", -1);
                for (String line : lines) {
                    document.add(new Paragraph(line));
                }
            }
            String artifactId = fileArtifactService.registerPdfArtifact(filePath, normalizedFileName, null);

            String txtDir = FileConstant.FILE_SAVE_DIR + "/file";
            FileUtil.mkdir(txtDir);
            String txtFileName = normalizedFileName.replaceAll("(?i)\\.pdf$", ".txt");
            String txtPath = txtDir + "/" + txtFileName;
            FileUtil.writeString(sanitizedContent, txtPath, StandardCharsets.UTF_8);
            String txtArtifactId = fileArtifactService.registerArtifact(txtPath, txtFileName, null);

            return "PDF 生成成功。artifactId=" + artifactId
                    + "; fileName=" + normalizedFileName
                    + "; txtArtifactId=" + txtArtifactId
                    + "; txtFileName=" + txtFileName;
        } catch (Exception e) {
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

    private String sanitizePdfContent(String content, PdfFont font) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        String normalized = content
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        StringBuilder sb = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\t') {
                sb.appendCodePoint(cp);
                return;
            }

            if (Character.isISOControl(cp)) {
                return;
            }

            if (cp > 0xFFFF) {
                sb.append(' ');
                return;
            }

            try {
                if (isRenderableByFont(font, cp)) {
                    sb.appendCodePoint(cp);
                } else {
                    sb.append(' ');
                }
            } catch (Exception ignored) {
                sb.append(' ');
            }
        });

        return sb.toString().replaceAll("[ ]{2,}", " ");
    }

    private boolean isRenderableByFont(PdfFont font, int codePoint) {
        if (codePoint <= 0 || codePoint > 0xFFFF) {
            return false;
        }

        if (!font.containsGlyph(codePoint)) {
            return false;
        }

        try {
            String ch = new String(Character.toChars(codePoint));
            float width = font.getWidth(ch, DEFAULT_FONT_SIZE);
            return !Float.isNaN(width) && width >= 0;
        } catch (Exception e) {
            return false;
        }
    }
}