package com.ahead.aiagent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * 终端操作工具类
 */

public class TerminalOperationTool {

    @Tool(description = "Execute a command in the terminal")
    public String executeTerminalCommand(@ToolParam(description = "Command to execute in the terminal") String command) {
        StringBuilder output = new StringBuilder();
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

            // 可选：设置工作目录为当前项目目录
            processBuilder.directory(new java.io.File(System.getProperty("user.dir")));

            // 3. 启动进程
            Process process = processBuilder.start();

            // 4. 读取标准输出
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 5. 读取标准错误（防止报错信息丢失或进程卡死）
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 && output.length() == 0) {
                output.append("Command execution failed with exit code: ").append(exitCode);
            }

        } catch (IOException | InterruptedException e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }
}
