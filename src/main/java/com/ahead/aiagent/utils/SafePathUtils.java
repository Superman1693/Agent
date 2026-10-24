package com.ahead.aiagent.utils;

import java.io.File;
import java.io.IOException;

/**
 * 文件路径安全工具。
 * 修复 E27/D24、E28：工具类中的 fileName 由模型提供，直接拼接会存在路径穿越与任意文件覆盖风险。
 */
public final class SafePathUtils {

    private SafePathUtils() {
    }

    /**
     * 净化文件名：剥离目录成分，替换非法字符，拒绝 "." / ".."。
     *
     * @param fileName    原始文件名
     * @param defaultName 兜底文件名
     * @return 安全的文件名（不含任何路径分隔符）
     */
    public static String sanitizeFileName(String fileName, String defaultName) {
        if (fileName == null || fileName.isBlank()) {
            return defaultName;
        }
        String base = fileName.replace('\\', '/');
        int idx = base.lastIndexOf('/');
        if (idx >= 0) {
            base = base.substring(idx + 1);
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
            return defaultName;
        }
        return base;
    }

    /**
     * 解析目标文件，并校验其必须落在 baseDir 目录之内（防路径穿越）。
     *
     * @param baseDir     允许写入的根目录
     * @param fileName    原始文件名
     * @param defaultName 兜底文件名
     * @return 位于 baseDir 内的安全 File
     * @throws IOException 当解析后的路径越出 baseDir 时抛出
     */
    public static File resolveWithin(String baseDir, String fileName, String defaultName) throws IOException {
        File dir = new File(baseDir).getCanonicalFile();
        String safeName = sanitizeFileName(fileName, defaultName);
        File target = new File(dir, safeName).getCanonicalFile();
        String dirPrefix = dir.getPath() + File.separator;
        if (!target.getPath().startsWith(dirPrefix)) {
            throw new IOException("非法的文件路径: " + fileName);
        }
        return target;
    }
}
