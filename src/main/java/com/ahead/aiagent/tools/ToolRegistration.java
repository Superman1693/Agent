package com.ahead.aiagent.tools;

import com.ahead.aiagent.service.FileArtifactService;
import com.ahead.aiagent.service.LocationService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

/**
 * 工具注册
 */
@Configuration
public class ToolRegistration {

    // 修复 E25/O21：加默认值，避免缺少 search-api.api-key 时应用启动失败
    @Value("${search-api.api-key:}")
    private String searchApiKey;

    // 安全修复 D9：终端命令工具默认关闭（LLM 可执行任意命令，黑名单可被绕过）。
    // 确需开启时设 AGENT_TOOLS_TERMINAL_ENABLED=true，并务必在容器/沙箱内运行。
    @Value("${app.agent.tools.terminal-enabled:false}")
    private boolean terminalEnabled;

    @Resource
    private FileArtifactService fileArtifactService;

    // 功能3：天气查询工具依赖的位置服务（代理 open-meteo）
    @Resource
    private LocationService locationService;

    /**
     * 单独的 WebSearchTool Bean（供 Service 层使用）
     */
    @Bean
    public WebSearchTool webSearchTool() {
        return new WebSearchTool(searchApiKey);
    }

    @Bean
    public ToolCallback[] allTools() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = webSearchTool();
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool(fileArtifactService);
        TerminateTool terminateTool = new TerminateTool();
        // 功能3：天气工具始终注册（内部自吞异常，不影响 Agent 链路）
        WeatherTool weatherTool = new WeatherTool(locationService);
        if (terminalEnabled) {
            return ToolCallbacks.from(
                    fileOperationTool,
                    webSearchTool,
                    webScrapingTool,
                    resourceDownloadTool,
                    new TerminalOperationTool(),
                    pdfGenerationTool,
                    weatherTool,
                    terminateTool);
        }
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                pdfGenerationTool,
                weatherTool,
                terminateTool);
    }
}
