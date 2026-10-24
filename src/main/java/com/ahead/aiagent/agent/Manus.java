package com.ahead.aiagent.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI智能体
 */
@Component
public class Manus extends  ToolCallAgent{

    public Manus(ToolCallback[] allTools,
                 @Value("${app.llm.decision-model:${spring.ai.dashscope.chat.options.model:qwen-plus}}") String modelName,
                 ChatClient manusChatClient) {
        super(allTools, modelName);
        this.setName("Manus");
        String SYSTEM_PROMPT = """  
                You are Manus, an all-capable AI assistant, aimed at solving any task presented by the user.  
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.  
                Important constraints:
                1) For itinerary/travel-planning requests (route planning, trip schedule, half-day/day plans), you SHOULD call generatePDF by default even if the user didn't explicitly mention PDF.
                2) For greetings or casual chat, do not call any tool; respond directly in natural language.
                3) If generatePDF is called, keep artifactId and txtArtifactId in the result so the system can return download links and text answer.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """  
                Based on user needs, proactively select the most appropriate tool or combination of tools.  
                For complex tasks, you can break down the problem and use different tools step by step to solve it.  
                After using each tool, clearly explain the execution results and suggest the next steps.  
                For itinerary/travel-planning requests, prefer calling generatePDF and produce both pdf artifact and txt artifact.
                For pure non-travel QA or small talk requests, avoid generatePDF.
                If the user only greets/chats, respond directly without tool calls.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.  
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 修复 O6：复用 Spring 容器中的单例 ChatClient（含 advisor 链），避免每请求重复构建
        this.setChatClient(manusChatClient);
    }
}
