package com.ahead.aiagent.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 修复 O14：为 {@link PDFGenerationTool#normalizePdfFileName(String)} 补单元测试。
 *
 * <p>该方法只做字符串净化（剥离非法文件名字符 + 补 .pdf 后缀），
 * 不触碰 iText 与文件系统，是纯逻辑，可脱离 Spring 上下文直接验证。</p>
 */
class PDFGenerationToolTest {

    /** normalizePdfFileName 不使用 FileArtifactService，注入 null 即可 */
    private final PDFGenerationTool tool = new PDFGenerationTool(null);

    private String normalize(String fileName) {
        try {
            Method method = PDFGenerationTool.class.getDeclaredMethod("normalizePdfFileName", String.class);
            method.setAccessible(true);
            return (String) method.invoke(tool, fileName);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射调用失败: normalizePdfFileName", e);
        }
    }

    @Test
    void blankNameFallsBackToDefault() {
        assertEquals("travel_plan.pdf", normalize(null));
        assertEquals("travel_plan.pdf", normalize("   "));
    }

    @Test
    void illegalPathCharactersAreReplaced() {
        assertEquals("a_b_c_d_e_f_g_h_i.pdf", normalize("a/b:c*d?e\"f<g>h|i"));
        assertEquals(".._etc_passwd.pdf", normalize("../etc/passwd"));
    }

    @Test
    void pdfSuffixIsAppendedOnlyWhenMissing() {
        assertEquals("plan.pdf", normalize("plan"));
        assertEquals("plan.PDF", normalize("plan.PDF"));
        assertEquals("plan.pdf", normalize("plan.pdf"));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertEquals("trip.pdf", normalize("  trip  "));
    }
}
