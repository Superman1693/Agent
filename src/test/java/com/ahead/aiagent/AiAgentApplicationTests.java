package com.ahead.aiagent;

import com.ahead.aiagent.app.TravelApp;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class AiAgentApplicationTests {

    @Resource
    private TravelApp travelApp;
    @Test
    void contextLoads() {
    }

    @Test
    void doChatWithReport() {
    	String chatId= UUID.randomUUID().toString();
        String message = "我想去杭州玩，请给我安排一下";
        TravelApp.TravelReport travelReport = travelApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(travelReport);
    }
    @Test
    void doChatWithRag() {
        String chatId= UUID.randomUUID().toString();
        String message = "旅游中手机没电又找不到充电处，有哪些应急解决办法？";
        String answer= travelApp.doChatWithRag(message, chatId);
        Assertions.assertNotNull(answer);
    }
    @Test
    void testChat() {

    }

}
