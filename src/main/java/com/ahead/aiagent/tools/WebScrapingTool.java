package com.ahead.aiagent.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

/**
 * 网页爬虫工具类
 */

public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url){
        try{
            Document document =Jsoup.connect(url).get();
            return document.html();
        }catch (Exception e){
            return "Error scraping web page:"+e.getMessage();
        }
    }

}
