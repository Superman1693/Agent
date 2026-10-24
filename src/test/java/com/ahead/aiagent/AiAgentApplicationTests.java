package com.ahead.aiagent;

import com.ahead.aiagent.app.TravelApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

/**
 * 修复 E37/O14：移除空测试方法；对必须调用真实外部 API（DashScope）的用例加 @Disabled，
 * 避免在无外部依赖的 CI 环境下误报失败。
 */
@SpringBootTest
class AiAgentApplicationTests {

    @Resource
    private TravelApp travelApp;

    @Test
    void contextLoads() {
        Assertions.assertNotNull(travelApp, "TravelApp 应被成功注入");
    }

    @Test
    @Disabled("需要真实 DashScope API Key 与网络，默认在 CI 中禁用")
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "我想去杭州玩，请给我安排一下";
        TravelApp.TravelReport travelReport = travelApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(travelReport);
    }

    @Test
    @Disabled("需要真实 DashScope API Key、向量库与网络，默认在 CI 中禁用")
    void doChatWithRag() {
        String chatId = UUID.randomUUID().toString();
        String message = "旅游中手机没电又找不到充电处，有哪些应急解决办法？";
        String answer = travelApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }

}
