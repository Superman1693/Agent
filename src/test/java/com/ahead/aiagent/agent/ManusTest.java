package com.ahead.aiagent.agent;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;



@SpringBootTest
class ManusTest {

    @Resource
    private  Manus manus;

    @Test
    void run() {
        String userPrompt = """  
                我住在江西软件职业技术大学，请帮我找到 5 公里内合适的旅游地点，
                每一个地点需要给我详细的介绍，
                并结合一些网络图片，制定一份详细的旅游计划，
                并以 PDF 格式输出，全程使用中文""";
        String answer = manus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}