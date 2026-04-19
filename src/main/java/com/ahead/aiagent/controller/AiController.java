package com.ahead.aiagent.controller;

import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.agent.Manus;
import com.ahead.aiagent.agent.model.AgentState;
import com.ahead.aiagent.app.TravelApp;
import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.service.FileArtifactService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/ai")
public class AiController {

    private static final Pattern TOOL_RESULT_PATTERN = Pattern.compile("工具\\s+([^\\s]+)\\s+完成了它的任务！结果:\\s*(.*)");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("artifactId\\s*[:=]\\s*\"?([A-Za-z0-9_-]+)\"?");

    @Resource
    private TravelApp travelApp;
    @Resource
    private ToolCallback[] allTools;
    @Resource
    private ChatModel dashscopeChatModel;
    @Resource
    private FileArtifactService fileArtifactService;
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * 将用户 ID 和前端传入的 chatId 拼接，保证不同用户的会话相互隔离。
     * chatId 不传时默认使用 "default"，每个用户有一个默认会话。
     */
    private String resolveSessionId(String chatId) {
        Long userId = AuthContext.getUserId();
        String sessionKey = (chatId != null && !chatId.isBlank()) ? chatId : "default";
        return userId + ":" + sessionKey;
    }

    /**
     * 同步调用 AI 旅游大师应用
     */
    @GetMapping("/travel_app/chat/sync")
    public BaseResponse<String> doChatWithTravelAppSync(String message, String chatId) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "message 不能为空");
        }
        return ResultUtils.success(travelApp.doChat(message, resolveSessionId(chatId)));
    }

    /**
     * SSE 流式调用（原生 text/event-stream）
     */
    @GetMapping(value = "/travel_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithTravelAppSSE(String message, String chatId) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "message 不能为空");
        }
        return travelApp.doChatByStream(message, resolveSessionId(chatId));
    }

    /**
     * SSE 流式调用（ServerSentEvent 封装）
     */
    @GetMapping("/travel_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithTravelAppServerSentEvent(String message, String chatId) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "message 不能为空");
        }
        return travelApp.doChatByStream(message, resolveSessionId(chatId))
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
    }

    /**
     * SSE 流式调用（SseEmitter 方式，推荐前端使用）
     */
    @GetMapping("/travel_app/chat/sse-emitter")
    public SseEmitter doChatWithTravelAppSseEmitter(String message, String chatId) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "message 不能为空");
        }
        SseEmitter sseEmitter = new SseEmitter(180000L);
        travelApp.doChatByStream(message, resolveSessionId(chatId))
                .subscribe(
                        chunk -> {
                            try {
                                sseEmitter.send(chunk);
                            } catch (Exception e) {
                                sseEmitter.completeWithError(e);
                            }
                        },
                        sseEmitter::completeWithError,
                        sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * SSE 流式调用 Manus 超级智能体
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message) {
        if (message == null || message.isBlank()) {
            throw new BusinessException(ErrorCode.NULL_ERROR, "message 不能为空");
        }
        Long userId = AuthContext.getUserId();
        Manus manus = new Manus(allTools, dashscopeChatModel);
        SseEmitter sseEmitter = new SseEmitter(300000L);
        String runId = "manus_" + UUID.randomUUID().toString().replace("-", "");

        CompletableFuture.runAsync(() -> executeManusWithStructuredEvents(manus, message, userId, runId, sseEmitter));
        sseEmitter.onTimeout(() -> sseEmitter.completeWithError(new RuntimeException("SSE connection timed out")));
        return sseEmitter;
    }

    private void executeManusWithStructuredEvents(Manus manus, String message, Long userId, String runId, SseEmitter sseEmitter) {
        try {
            manus.setState(AgentState.RUNNING);
            manus.getMessageList().add(new UserMessage(buildChineseConstrainedPrompt(message)));

            sendEvent(sseEmitter, "thought", runId, 0, Map.of("content", "开始分析并规划执行步骤"));

            List<String> finalParts = new ArrayList<>();
            boolean emittedToolCall = false;
            int lastStep = 0;
            Set<String> emittedArtifactIds = new HashSet<>();

            for (int i = 0; i < manus.getMaxSteps() && manus.getState() != AgentState.FINISHED; i++) {
                int step = i + 1;
                lastStep = step;
                manus.setCurrentStep(step);
                String stepResult = manus.step();
                if (stepResult == null) {
                    stepResult = "";
                }

                sendEvent(sseEmitter, "thought", runId, step, Map.of("content", summarizeThought(stepResult)));

                String[] lines = stepResult.split("\\R");
                for (String line : lines) {
                    Matcher toolMatcher = TOOL_RESULT_PATTERN.matcher(line);
                    if (toolMatcher.find()) {
                        emittedToolCall = true;
                        String toolName = toolMatcher.group(1);
                        String toolResult = toolMatcher.group(2);
                        sendEvent(sseEmitter, "tool_call", runId, step,
                                Map.of("name", toolName, "arguments", "由智能体自主决策调用"));
                        sendEvent(sseEmitter, "tool_result", runId, step,
                            Map.of("name", toolName, "summary", summarizeToolResult(toolResult)));
                    }
                }

                // 兜底：从整段 step 文本提取 artifactId，避免因为格式差异漏发 artifact 事件
                emitArtifactEventIfPresent(stepResult, userId, runId, step, emittedArtifactIds, sseEmitter);

                String cleanStep = sanitizeForClient(stepResult);
                if (!cleanStep.isBlank()) {
                    finalParts.add(cleanStep);
                }
            }

            if (manus.getState() != AgentState.FINISHED && lastStep >= manus.getMaxSteps()) {
                manus.setState(AgentState.FINISHED);
            }

            if (!emittedToolCall) {
                sendEvent(sseEmitter, "tool_call", runId, lastStep,
                        Map.of("name", "none", "arguments", "本轮无需调用外部工具"));
            }

            String finalSummary = buildFinalSummary(finalParts, emittedArtifactIds);
            sendEvent(sseEmitter, "final", runId, lastStep, Map.of("content", finalSummary));
            sendEvent(sseEmitter, "done", runId, lastStep, Map.of("status", "finished"));
            sseEmitter.complete();
        } catch (Exception e) {
            sendEvent(sseEmitter, "error", runId, manus.getCurrentStep(), Map.of("message", toChineseErrorMessage(e)));
            sseEmitter.completeWithError(e);
        } finally {
            manus.setState(AgentState.FINISHED);
        }
    }

    private String buildChineseConstrainedPrompt(String message) {
        return "请始终使用简体中文回复。"
                + "如果用户明确要求生成 PDF，必须调用生成 PDF 工具，并在结果中保留 artifactId 以便系统返回下载链接。"
                + "禁止输出超长原始 HTML 或大段 JSON，优先给出简洁中文摘要。"
                + "\n用户原始需求：" + message;
    }

    private String summarizeThought(String text) {
        String clean = sanitizeForClient(text);
        String lower = clean.toLowerCase();
        if (lower.contains("<html") || lower.contains("<!doctype")) {
            return "已获取网页内容，正在提取关键信息。";
        }
        if ((clean.startsWith("{") || clean.startsWith("[")) && clean.length() > 120) {
            return "已获取结构化数据，正在整理结果。";
        }
        return summarize(clean, 120);
    }

    private String summarizeToolResult(String text) {
        String clean = sanitizeForClient(text);
        String lower = clean.toLowerCase();
        if (clean.contains("artifactId=")) {
            return "PDF 已生成，正在准备下载链接。";
        }
        if (lower.contains("<html") || lower.contains("<!doctype")) {
            return "工具返回网页 HTML，正文已省略，仅保留摘要。";
        }
        if ((clean.startsWith("{") || clean.startsWith("[")) && clean.length() > 120) {
            return "工具返回结构化数据，已提取关键摘要。";
        }
        return summarize(clean, 160);
    }

    private String buildFinalSummary(List<String> finalParts, Set<String> emittedArtifactIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务已完成，已基于你的要求执行智能体流程并整理结果。");
        if (!emittedArtifactIds.isEmpty()) {
            sb.append(" 已生成 PDF，请使用下载链接获取文件。");
        } else {
            sb.append(" 本轮未检测到可下载产物。");
        }
        if (!finalParts.isEmpty()) {
            String merged = summarize(String.join(" ", finalParts), 180);
            if (!merged.isBlank()) {
                sb.append(" 结果摘要：").append(merged);
            }
        }
        return sb.toString();
    }

    private String toChineseErrorMessage(Exception e) {
        String msg = (e == null || e.getMessage() == null) ? "系统处理失败" : e.getMessage();
        return "执行过程中出现异常：" + sanitizeForClient(msg);
    }

    private void emitArtifactEventIfPresent(String text,
                                            Long userId,
                                            String runId,
                                            int step,
                                            Set<String> emittedArtifactIds,
                                            SseEmitter sseEmitter) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher artifactMatcher = ARTIFACT_ID_PATTERN.matcher(text);
        while (artifactMatcher.find()) {
            String artifactId = artifactMatcher.group(1);
            if (!emittedArtifactIds.add(artifactId)) {
                continue;
            }
            fileArtifactService.bindOwner(artifactId, userId);
            fileArtifactService.getArtifact(artifactId).ifPresent(meta -> {
                Map<String, Object> artifactData = new LinkedHashMap<>();
                artifactData.put("artifactId", meta.artifactId());
                artifactData.put("fileId", meta.artifactId());
                artifactData.put("name", meta.fileName());
                artifactData.put("mimeType", "application/pdf");
                artifactData.put("size", meta.size());
                artifactData.put("downloadUrl", buildDownloadUrl(meta.artifactId(), meta.downloadToken()));
                artifactData.put("expiresAt", meta.expireAt().toString());
                sendEvent(sseEmitter, "artifact", runId, step, artifactData);
            });
        }
    }

    private String buildDownloadUrl(String artifactId, String token) {
        String cp = (contextPath == null) ? "" : contextPath.trim();
        if (!cp.isEmpty() && !cp.startsWith("/")) {
            cp = "/" + cp;
        }
        if ("/".equals(cp)) {
            cp = "";
        }
        return cp + "/files/" + artifactId + "/download?token=" + token;
    }

    private void sendEvent(SseEmitter sseEmitter, String type, String runId, int step, Object data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("runId", runId);
        payload.put("step", step);
        payload.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("data", data);
        try {
            sseEmitter.send(JSONUtil.toJsonStr(payload));
        } catch (Exception e) {
            throw new RuntimeException("发送 SSE 事件失败", e);
        }
    }

    private String sanitizeForClient(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replaceAll("[A-Za-z]:\\\\[^\\s]+", "[local-path-hidden]");
        return cleaned.replaceAll("\\s+", " ").trim();
    }

    private String summarize(String text, int limit) {
        String clean = sanitizeForClient(text);
        if (clean.length() <= limit) {
            return clean;
        }
        return clean.substring(0, limit) + "...(已截断)";
    }
}
