package com.ahead.aiagent.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * 网页爬虫工具类
 */

public class WebScrapingTool {

    /** 正文最大返回长度，防止整页 HTML 撑爆模型上下文 */
    private static final int MAX_CONTENT_CHARS = 8000;

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url).timeout(10_000).get();
            // 修复 O3：只返回正文纯文本，而不是整段 HTML，避免上万 token 随步数线性累积
            String text = document.body() != null ? document.body().text() : document.text();
            if (text.length() > MAX_CONTENT_CHARS) {
                text = text.substring(0, MAX_CONTENT_CHARS) + "...(已截断)";
            }
            return text;
        } catch (Exception e) {
            return "Error scraping web page:" + e.getMessage();
        }
    }

}
