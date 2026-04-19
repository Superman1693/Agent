package com.ahead.imagesearchmcpserver.tools;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    private static final String API_KEY = "fVCsVgtgxpOsTnHSuHBF18xrAIz1yAQwR2uCTYCRDowxSBqL8NPweWyP";


    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            return String.join(",", searchMediumImages(query));
        } catch (Exception e) {
            return "Error search image: " + e.getMessage();
        }
    }

    /**
     * 搜索中等分辨率的图片
     *
     * @param query 查询关键字
     * @return 图片链接列表
     */

    public List<String> searchMediumImages(String query) {
        // 设置请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置参数
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        // 发送请求
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析响应
        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photoObj -> photoObj.getJSONObject("src"))
                .map(photo -> photo.getStr("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

}
