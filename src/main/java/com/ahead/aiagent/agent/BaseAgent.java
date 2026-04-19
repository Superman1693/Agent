package com.ahead.aiagent.agent;

import com.ahead.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.internal.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程
 *
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能
 * 子类必须实现step()方法
 */
@Data
@Slf4j
public abstract class BaseAgent {

    //核心属性
    private String name;
    //系统提示
    private String systemPrompt;
    private String nextStepPrompt;
    //代理状态
    private AgentState state = AgentState.IDLE;
    //执行步骤控制
    private int maxSteps = 10;
    private int currentStep = 0;
    //LLM大模型
    private ChatClient chatClient;
    //Memory记忆（自主维护会话上下文）
    private List<Message> messageList = new ArrayList<>();

    /**
     * 运行代理
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public  String run(String userPrompt) {
        //基础校验
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("Cannot run agent from state: " + this.state);
        }
        if (StringUtil.isBlank(userPrompt)) {
            throw new RuntimeException("Cannot run agent with empty user prompt");
        }
        //执行，更改状态
        state = AgentState.RUNNING;
        //记录消息上下文
        messageList.add(new UserMessage(userPrompt));
        //保存结果列表，用于返回给用户
        List<String> results = new ArrayList<>();
        try {
            //循环执行，直到完成或达到最大步骤数
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step " + stepNumber + "/" + maxSteps);
                //执行单个步骤
                String stepResult = step();
                String result = "Step " + stepNumber + ": " + stepResult;
                results.add(result);
            }
            //检查是否超出步骤限制
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("Error executing agent", e);
            return "执行错误" + e.getMessage();
        } finally {

            this.cleanup();
        }
    }
    /**
     * 运行代理（流式输出）
     * @param userPrompt 用户提示词
     * @return 执行结果
     */
    public  SseEmitter runStream(String userPrompt) {
        //创建一个超时时间较长的SseEmitter
        SseEmitter sseEmitter = new SseEmitter(300000L);//5分钟超时
        //使用线程异步处理，避免阻塞主线程
        CompletableFuture.runAsync(() -> {

            try {
                //基础校验
                if (this.state != AgentState.IDLE) {
                    sseEmitter.send("错误：无法从状态运行代理："+this.state);
                    sseEmitter.complete();
                    return;
                }
                if (StringUtil.isBlank(userPrompt)) {
                    sseEmitter.send("错误：不能使用空提示词运行代理");
                    sseEmitter.complete();
                    return;
                }

            }catch (Exception e){
                sseEmitter.completeWithError(e);
            }
            //执行，更改状态
            state = AgentState.RUNNING;
            //记录消息上下文
            messageList.add(new UserMessage(userPrompt));
            //保存结果列表，用于返回给用户
            List<String> results = new ArrayList<>();
            try {
                //循环执行，直到完成或达到最大步骤数
                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step " + stepNumber + "/" + maxSteps);
                    //执行单个步骤
                    String stepResult = step();
                    String result = "Step " + stepNumber + ": " + stepResult;
                    results.add(result);
                    //输出当前每一步的结果到SSE
                    sseEmitter.send(result);
                }
                //检查是否超出步骤限制
                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    results.add("Terminated: Reached max steps (" + maxSteps + ")");
                    sseEmitter.send("执行结束：达到最大步骤 (" + maxSteps + ")");
                }
                //正常完成
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("Error executing agent", e);
                try {
                    sseEmitter.send("执行错误" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                this.cleanup();
            }
        });
        //设置超时回调
        sseEmitter.onTimeout(() -> {
            this.state=AgentState.ERROR;
            this.cleanup();
            log.warn("SSEEmitter connection timed out");
        });
        sseEmitter.onCompletion(() -> {
            if(this.state==AgentState.RUNNING){
                this.state=AgentState.FINISHED;
            }
            this.cleanup();
            log.info("SSEEmitter connection completed");
        });
        return sseEmitter;
    }

    /**
     * 定义单个步骤
     * @return
     */
    public abstract String step();


    /**
     * 清理资源
     */
    protected void cleanup() {

    }
}
