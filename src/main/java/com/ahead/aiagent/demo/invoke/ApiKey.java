package com.ahead.aiagent.demo.invoke;

/**
 * 安全修复：密钥一律走环境变量，禁止硬编码进源码（原硬编码 Key 已随历史提交泄露，请到阿里云百炼控制台轮换）。
 */
public interface ApiKey {

    String API_KEY = System.getenv("DASHSCOPE_API_KEY");
}
