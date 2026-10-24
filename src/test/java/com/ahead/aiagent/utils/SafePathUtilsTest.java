package com.ahead.aiagent.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

/**
 * 修复 E37/O14：为新增的文件路径安全工具补充可离线运行的单元测试。
 */
class SafePathUtilsTest {

    @Test
    void sanitizeShouldStripDirectoryComponents() {
        Assertions.assertEquals("passwd", SafePathUtils.sanitizeFileName("../../etc/passwd", "default"));
        Assertions.assertEquals("config.yml", SafePathUtils.sanitizeFileName("..\\..\\config.yml", "default"));
        Assertions.assertEquals("a.txt", SafePathUtils.sanitizeFileName("/abs/path/a.txt", "default"));
    }

    @Test
    void sanitizeShouldRejectDotOnlyNames() {
        Assertions.assertEquals("default", SafePathUtils.sanitizeFileName("..", "default"));
        Assertions.assertEquals("default", SafePathUtils.sanitizeFileName(".", "default"));
        Assertions.assertEquals("default", SafePathUtils.sanitizeFileName("   ", "default"));
        Assertions.assertEquals("default", SafePathUtils.sanitizeFileName(null, "default"));
    }

    @Test
    void resolveWithinShouldKeepFileInsideBaseDir() throws IOException {
        File base = new File(System.getProperty("java.io.tmpdir"), "safe-path-test");
        File target = SafePathUtils.resolveWithin(base.getAbsolutePath(), "a.txt", "default.txt");
        Assertions.assertEquals(base.getCanonicalPath(), target.getParentFile().getCanonicalPath());
    }

    @Test
    void resolveWithinShouldNeutralizeTraversal() throws IOException {
        File base = new File(System.getProperty("java.io.tmpdir"), "safe-path-test");
        File target = SafePathUtils.resolveWithin(base.getAbsolutePath(), "../../evil.sh", "default.bin");
        // 净化后应仍落在 base 目录内，而不是逃逸到上层目录
        Assertions.assertEquals(base.getCanonicalPath(), target.getParentFile().getCanonicalPath());
    }
}
