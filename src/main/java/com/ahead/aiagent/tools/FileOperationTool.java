package com.ahead.aiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.ahead.aiagent.constant.FileConstant;
import com.ahead.aiagent.utils.SafePathUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;

/**
 * 文件操作工具类(提供文件读写功能)
 */
public class FileOperationTool {

    private final String FILE_DIR = FileConstant.FILE_SAVE_DIR + "/file";

    @Tool(description = "Read content from a file")
    public String readFile(@ToolParam(description = "Name of a file to read") String fileName) {
        try {
            // 修复 E28：净化并校验路径，防止越权读取任意文件
            File target = SafePathUtils.resolveWithin(FILE_DIR, fileName, "file.txt");
            return FileUtil.readUtf8String(target);
        } catch (Exception e) {
            return "Error reading file:" + e.getMessage();
        }
    }

    @Tool(description = "Write content to a file")
    public String writeFile(@ToolParam(description = "Name of a file to write") String fileName,
                            @ToolParam(description = "Content to write to the file") String content) {
        try {
            // 创建目录
            FileUtil.mkdir(FILE_DIR);
            // 修复 E28：净化并校验路径，防止路径穿越 / 任意文件覆盖
            File target = SafePathUtils.resolveWithin(FILE_DIR, fileName, "file.txt");
            FileUtil.writeUtf8String(content, target);
            return "File written successfully to" + target.getPath();
        } catch (Exception e) {
            return "Error writing file:" + e.getMessage();
        }
    }
}
