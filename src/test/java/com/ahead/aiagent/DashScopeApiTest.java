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
public class DashScopeApiTest {

    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY");

    @Test
    public void testAllModels() throws Exception {
        String[] models = {"qwen-plus", "qwen-max", "qwen-turbo", "qwen3.6-plus", "qwen2.5-plus"};
        
        for (String model : models) {
            System.out.println("\n测试模型: " + model);
            try {
                Generation gen = new Generation();
                Message userMsg = Message.builder()
                        .role(Role.USER.getValue())
                        .content("你好")
                        .build();
                GenerationParam param = GenerationParam.builder()
                        .apiKey(API_KEY)
                        .model(model)
                        .messages(Arrays.asList(userMsg))
                        .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                        .build();
                GenerationResult result = gen.call(param);
                System.out.println("✅ 成功!");
            } catch (Exception e) {
                System.out.println("❌ 失败: " + e.getClass().getSimpleName());
            }
        }
    }
}
