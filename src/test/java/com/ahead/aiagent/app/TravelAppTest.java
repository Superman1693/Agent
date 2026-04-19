package com.ahead.aiagent.app;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
@SpringBootTest
@Slf4j
class TravelAppTest {

    @Resource
    private TravelApp travelApp;
    @Test
    void doChat() {
    }

    @Test
    void doChatWithReport() {
    }

    @Test
    void doChatWithRag() {
    }

    @Test
    void doChatWithTools() {

        String chatId = UUID.randomUUID().toString();
        testMessage("周末想带女朋友去上海旅游，推荐几个适合情侣的小众打卡地？", chatId);


        testMessage("最近想去旅游了，看看编程知识星球（e-ren.icu）的其他人去哪旅游了？", chatId);


        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件", chatId);


        testMessage("执行 Python3 脚本来生成数据分析报告", chatId);


        testMessage("保存我的旅游档案为文件", chatId);


        testMessage("生成一份‘国庆旅游计划’PDF，包含生活常识、注意事项和景点清单", chatId);
    }

    private void testMessage(String message,String chatId) {

        String answer = travelApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
        log.info("answer:{}", answer);
    }

    @Test
    void doChatWithMcp() {

        String chatId = UUID.randomUUID().toString();
        //测试地图MCP
//        String answer= travelApp.doChatWithMcp(
//                "我现在在江西软件职业技术大学，我想去游玩，请给我安排一下5公里内的打卡景点", chatId);
//        Assertions.assertNotNull(answer);
        //测试图片搜索MCP
        String answer= travelApp.doChatWithMcp(
                "帮我搜索一下关于中国漂亮女孩的图片", chatId);
        Assertions.assertNotNull(answer);

    }
}