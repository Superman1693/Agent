package com.ahead.aiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具类
 */

public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        return search(query);
    }

    /**
     * 搜索方法（供外部直接调用）
     */
    public String search(String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);

            JSONObject jsonObject = JSONUtil.parseObj(response);

            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            // 修复 E32/D22：organic_results 为 null 或元素不足 5 个时会抛 NPE / IndexOutOfBoundsException，
            // 过去被 catch 兜住后把「结果不足」误报为「搜索失败」。这里显式判空并限制下标。
            if (organicResults == null || organicResults.isEmpty()) {
                return "未找到与「" + query + "」相关的搜索结果。";
            }
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));

            String result = objects.stream().map(obj -> {
                JSONObject tmpJSONObject = (JSONObject) obj;
                return tmpJSONObject.toString();
            }).collect(Collectors.joining(","));
            return result;
        } catch (Exception e) {
            return "Error searching Baidu: " + e.getMessage();
        }
    }

}
