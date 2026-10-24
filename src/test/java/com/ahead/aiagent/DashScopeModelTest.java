package com.ahead.aiagent;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Arrays;

/**
 * 安全修复：密钥改从环境变量读取，未配置 DASHSCOPE_API_KEY 时整类跳过，不再硬编码进源码。
 */
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class DashScopeModelTest {

    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY");

    @Test
    public void testQwen36Plus() throws ApiException, NoApiKeyException, InputRequiredException {
        System.out.println("测试 qwen3.6-plus...");
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你好")
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(API_KEY)
                .model("qwen3.6-plus")
                .messages(Arrays.asList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        GenerationResult result = gen.call(param);
        System.out.println("成功: " + JsonUtils.toJson(result));
    }

    @Test
    public void testQwenPlus() throws ApiException, NoApiKeyException, InputRequiredException {
        System.out.println("测试 qwen-plus...");
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你好")
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(API_KEY)
                .model("qwen-plus")
                .messages(Arrays.asList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        GenerationResult result = gen.call(param);
        System.out.println("成功: " + JsonUtils.toJson(result));
    }

    @Test
    public void testQwenMax() throws ApiException, NoApiKeyException, InputRequiredException {
        System.out.println("测试 qwen-max...");
        Generation gen = new Generation();
        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content("你好")
                .build();
        GenerationParam param = GenerationParam.builder()
                .apiKey(API_KEY)
                .model("qwen-max")
                .messages(Arrays.asList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .build();
        GenerationResult result = gen.call(param);
        System.out.println("成功: " + JsonUtils.toJson(result));
    }
}
