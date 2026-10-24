package com.ahead.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpUtil;
import com.ahead.aiagent.constant.FileConstant;
import com.ahead.aiagent.utils.SafePathUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 资源下载工具类
 */

public class ResourceDownloadTool {

    @Tool(description = "Download a resource from a given URL")
    public String downloadResource(@ToolParam(description = "URL of the resource to download") String url, @ToolParam(description = "Name of the file to save the downloaded resource") String fileName) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/download";
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);
            // 修复 E27/D24：fileName 由模型提供，必须净化并校验落在 fileDir 内，防止路径穿越 / 任意文件覆盖
            File target = SafePathUtils.resolveWithin(fileDir, fileName, "download.bin");
            // 使用Hutool的downloadFile方法，下载资源
            HttpUtil.downloadFile(url, target);
            return "Resource downloaded successfully to: " + target.getPath();
        } catch (Exception e) {
            return "Error downloading resource: " + e.getMessage();
        }
    }
}
