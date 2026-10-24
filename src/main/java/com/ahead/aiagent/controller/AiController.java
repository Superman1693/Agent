package com.ahead.aiagent.controller;

import cn.hutool.json.JSONUtil;
import com.ahead.aiagent.agent.Manus;
import com.ahead.aiagent.agent.model.AgentState;
import com.ahead.aiagent.app.TravelApp;
import com.ahead.aiagent.common.BaseResponse;
import com.ahead.aiagent.common.ErrorCode;
import com.ahead.aiagent.common.ResultUtils;
import com.ahead.aiagent.exception.BusinessException;
import com.ahead.aiagent.metrics.AgentMetrics;
import com.ahead.aiagent.security.AuthContext;
import com.ahead.aiagent.model.vo.AgentTraceVO;
import com.ahead.aiagent.service.AgentTraceService;
import com.ahead.aiagent.service.FileArtifactService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    private static final Pattern TOOL_RESULT_PATTERN = Pattern.compile("工具\\s+([^\\s]+)\\s+完成了它的任务！结果:\\s*(.*)");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile(
            "(?i)(?:txt)?artifactid\\s*[:=]\\s*\"?([A-Za-z0-9_-]+)\"?");
    /** 安全修复 E13/D10：chatId 会参与文件名拼接，做字符白名单校验 */
    private static final Pattern CHAT_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Resource
    private TravelApp travelApp;
    @Resource
    private ToolCallback[] allTools;
    @Resource
    private FileArtifactService fileArtifactService;
    /** 修复 O23：Agent 过程回溯（完整 trace 落库与查询） */
    @Resource
    private AgentTraceService agentTraceService;
    /** 修复 O1：使用专用线程池替代 ForkJoinPool.commonPool()，避免长耗时 Agent 任务挤占公共池 */
    @Resource(name = "agentTaskExecutor")
    private Executor agentTaskExecutor;
    /** 修复 O13：AI 链路可观测性指标 */
    @Resource
    private AgentMetrics agentMetrics;
    @Value("${server.servlet.context-path:}")
    private String contextPath;
    /** 修复 O4/O6：决策步骤使用主模型（app.llm.decision-model），ChatClient 复用容器单例 */
    @Value("${app.llm.decision-model:${spring.ai.dashscope.chat.options.model:qwen-plus}}")
    private String modelName;
    @Resource(name = "manusChatClient")
    private ChatClient manusChatClient;

    /**
     * 将用户 ID 和前端传入的 chatId 拼接，保证不同用户的会话相互隔离。
     * chatId 不传时默认使用 "default"，每个用户有一个默认会话。
     */
    private String resolveSessionId(String chatId) {
        Long userId = AuthContext.getUserId();
        String sessionKey = (chatId != null && !chatId.isBlank()) ? chatId.trim() : "default";
        // 修复 E13/D10：chatId 最终会拼成 "{userId}:{chatId}.kryo" 文件名，
        // 过去仅靠平台非法字符「碰巧」拦截，这里改为显式白名单校验。
        if (!CHAT_ID_PATTERN.matcher(sessionKey).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "chatId 仅允许字母、数字、下划线和中划线，长度 1-64");
        }
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
        Manus manus = new Manus(allTools, modelName, manusChatClient);
        SseEmitter sseEmitter = new SseEmitter(300000L);
        String runId = "manus_" + UUID.randomUUID().toString().replace("-", "");

        // 修复 O13：记录任务开始时间与计数，用于成功率 / 耗时 / 步数指标
        long startAt = System.currentTimeMillis();
        agentMetrics.taskStarted();

        // 修复 E15/D17：异步线程无法继承请求线程的 ThreadLocal，这里在任务内显式恢复认证上下文。
        // 修复 O1：改用专用线程池 agentTaskExecutor，替代默认的 ForkJoinPool.commonPool()。
        CompletableFuture.runAsync(() -> {
            AuthContext.set(userId, "jwt");
            try {
                executeManusWithStructuredEvents(manus, message, userId, runId, sseEmitter, startAt);
            } finally {
                AuthContext.clear();
            }
        }, agentTaskExecutor);
        // 修复 O13：SSE 异常断开打点
        sseEmitter.onError(t -> agentMetrics.sseDisconnected());
        sseEmitter.onTimeout(() -> sseEmitter.completeWithError(new RuntimeException("SSE connection timed out")));
        return sseEmitter;
    }

    private void executeManusWithStructuredEvents(Manus manus, String message, Long userId, String runId,
                                                  SseEmitter sseEmitter, long startAt) {
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
                        // 修复 O13：工具调用分布打点
                        agentMetrics.toolCalled(toolName);
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
                // 修复 O13：超步数终止打点
                agentMetrics.taskTerminatedByMaxSteps();
            }

            if (!emittedToolCall) {
                sendEvent(sseEmitter, "tool_call", runId, lastStep,
                        Map.of("name", "none", "arguments", "本轮无需调用外部工具"));
            }

            String finalSummary = buildFinalSummary(finalParts, emittedArtifactIds);
            sendEvent(sseEmitter, "final", runId, lastStep, Map.of("content", finalSummary));
            sendEvent(sseEmitter, "done", runId, lastStep, Map.of("status", "finished"));
            // 修复 O13：成功完成打点（耗时 + 步数）
            agentMetrics.taskSucceeded(System.currentTimeMillis() - startAt, lastStep);
            safeComplete(sseEmitter);
        } catch (Exception e) {
            log.error("Manus 任务执行失败, runId={}", runId, e);
            // 修复 O13：失败打点
            agentMetrics.taskFailed();
            sendEvent(sseEmitter, "error", runId, manus.getCurrentStep(), Map.of("message", toChineseErrorMessage(e)));
            safeCompleteWithError(sseEmitter, e);
        } finally {
            manus.setState(AgentState.FINISHED);
        }
    }

    /** 修复 E14/D16：客户端已断开时 complete/completeWithError 也可能抛异常，统一吞掉避免影响任务收尾 */
    private void safeComplete(SseEmitter sseEmitter) {
        try {
            sseEmitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete 失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private void safeCompleteWithError(SseEmitter sseEmitter, Throwable e) {
        try {
            sseEmitter.completeWithError(e);
        } catch (Exception ex) {
            log.warn("SSE completeWithError 失败（客户端可能已断开）: {}", ex.getMessage());
        }
    }

    private String buildChineseConstrainedPrompt(String message) {
        return "请始终使用简体中文回复。"
                + "当用户提出行程规划/路线安排/旅游计划（如半日游、一日游、多日游）时，即使未显式提到 PDF，也应默认调用 generatePDF 生成可下载行程单。"
                + "若用户只是问候、闲聊、问答、解释概念，禁止调用 generatePDF。"
                + "若已调用 generatePDF，结果中保留 artifactId 与 txtArtifactId 以便系统返回下载链接。"
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
                String mimeType = resolveMimeType(meta.fileName());
                Map<String, Object> artifactData = new LinkedHashMap<>();
                artifactData.put("artifactId", meta.artifactId());
                artifactData.put("fileId", meta.artifactId());
                artifactData.put("name", meta.fileName());
                artifactData.put("mimeType", mimeType);
                artifactData.put("size", meta.size());
                artifactData.put("downloadUrl", buildDownloadUrl(meta.artifactId(), meta.downloadToken()));
                artifactData.put("expiresAt", meta.expireAt().toString());
                sendEvent(sseEmitter, "artifact", runId, step, artifactData);
            });
        }
    }

    private String resolveMimeType(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        }
        return "application/octet-stream";
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

    /** 修复 O23：查询某次 Agent 任务的完整过程（需先开启 app.trace.persist-enabled 并执行 db/agent_trace.sql） */
    @GetMapping("/trace")
    public List<AgentTraceVO> getAgentTrace(@RequestParam String runId) {
        return agentTraceService.listByRunId(runId, AuthContext.getUserId());
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
            // 修复 E14/D16：客户端断开（切页、网络抖动）是 SSE 发送失败的最常见原因，
            // 过去这里抛 RuntimeException 会中断整个 Agent 任务，导致已生成的产物无法下发。
            // 现在仅记录日志，让任务继续跑完（产物仍会入库，可通过下载链接或后续列表找回）。
            log.warn("发送 SSE 事件失败（客户端可能已断开），type={}, runId={}, step={}: {}",
                    type, runId, step, e.getMessage());
        } finally {
            // 修复 O23：无论发送成功与否都记录完整 trace（客户端断连时事件会丢失，落库后可事后回溯）
            agentTraceService.recordAsync(runId, AuthContext.getUserId(), step, type, JSONUtil.toJsonStr(data));
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
