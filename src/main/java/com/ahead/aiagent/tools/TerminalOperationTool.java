package com.ahead.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 终端操作工具类
 */
public class TerminalOperationTool {

    /** 命令执行超时（秒） */
    private static final long TIMEOUT_SECONDS = 30;
    /** 输出上限（字符数），防止超长输出撑爆上下文 */
    private static final int MAX_OUTPUT_CHARS = 100_000;

    /**
     * 安全修复 D9：对明显破坏性命令做拦截。
     * 说明：完全白名单会破坏 Agent 的通用性，这里采用「黑名单 + 超时 + 输出上限 + 工作目录限制」
     * 的组合降低 prompt injection 引发的风险；生产环境建议进一步改用容器/沙箱隔离。
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("(?i)\\brm\\s+-rf\\s+/"),
            Pattern.compile("(?i)\\bmkfs\\b"),
            Pattern.compile("(?i)\\bformat\\s+[a-z]:"),
            Pattern.compile("(?i)\\bdel\\s+/[sf]\\b"),
            Pattern.compile("(?i)\\bshutdown\\b"),
            Pattern.compile("(?i)\\breboot\\b"),
            Pattern.compile(":\\(\\)\\s*\\{"),
            Pattern.compile("(?i)\\bdd\\s+if=.*of=/dev/"),
            Pattern.compile("(?i)>\\s*/dev/sd[a-z]")
    );

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
        if (command == null || command.isBlank()) {
            return "Error: command is empty";
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(command).find()) {
                return "Error: 该命令被安全策略拒绝执行（命中高危模式）。";
            }
        }
        Process process = null;
        try {
            // 1. 获取操作系统名称
            String os = System.getProperty("os.name").toLowerCase();

            ProcessBuilder processBuilder;

            // 2. 根据操作系统选择执行器
            if (os.contains("win")) {
                // Windows 使用 cmd.exe /c
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                // Linux/Mac 使用 /bin/sh -c
                processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            }

            // 3. 工作目录限定在当前项目目录，避免越权访问其它路径
            processBuilder.directory(new java.io.File(System.getProperty("user.dir")));

            // 修复 E26/D23：合并 stdout/stderr，避免「先读 stdout 到 EOF 再读 stderr」时
            // 子进程写满 stderr 管道缓冲导致双向等待、永久挂起。
            processBuilder.redirectErrorStream(true);

            // 4. 启动进程
            process = processBuilder.start();

            // 5. 读取合并后的输出，并限制最大长度
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() < MAX_OUTPUT_CHARS) {
                        output.append(line).append("\n");
                    }
                }
            }

            // 6. 修复 E26/D23：waitFor 增加超时，超时后强制终止，避免 Agent 任务永久卡住
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                output.append("\n[命令执行超时（").append(TIMEOUT_SECONDS).append("s），已强制终止]");
                return output.toString();
            }

            int exitCode = process.exitValue();
            if (exitCode != 0 && output.length() == 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            output.append("Error executing command: ").append(e.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return output.toString();
    }
}
