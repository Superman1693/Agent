package com.ahead.aiagent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

/**
 * 安全修复：密钥改从环境变量读取，未配置 DASHSCOPE_API_KEY 时整类跳过，不再硬编码进源码。
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class DashScopeModelNameTest {

    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY");

    @Test
    public void testModelNames() throws Exception {
        // 尝试不同的模型名称格式
        String[] models = {
            "qwen-plus",
            "qwen-max", 
            "qwen-turbo",
            "qwen-max-latest",
            "qwen-plus-latest",
            "qwen2.5-72b-instruct",
            "qwen2.5-32b-instruct",
            "qwen3-72b-instruct",
            "qwen3-8b-instruct"
        };
        
        for (String model : models) {
            System.out.print("Testing " + model + ": ");
            try {
                Generation gen = new Generation();
                Message userMsg = Message.builder()
                        .role(Role.USER.getValue())
                        .content("Hello")
                        .build();
                GenerationParam param = GenerationParam.builder()
                        .apiKey(API_KEY)
                        .model(model)
                        .messages(Arrays.asList(userMsg))
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .build();
                GenerationResult result = gen.call(param);
                System.out.println("OK");
            } catch (Exception e) {
                System.out.println("FAILED");
            }
        }
    }
}
