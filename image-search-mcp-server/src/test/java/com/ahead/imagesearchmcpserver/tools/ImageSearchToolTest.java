package com.ahead.imagesearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 修复 E37：移除空测试方法；对需要真实 Pexels Key 的用例加 @Disabled。
 */
@SpringBootTest
class ImageSearchToolTest {


    @Resource
    private ImageSearchTool imageSearchTool;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(imageSearchTool, "ImageSearchTool 应被成功注入");
    }

    @Test
    @Disabled("需要真实 Pexels API Key 与网络，默认在 CI 中禁用")
    void searchImage() {
        String result = imageSearchTool.searchImage("girl");
        Assertions.assertNotNull(result);
    }
}
