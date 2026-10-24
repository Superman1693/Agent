package com.ahead.aiagent.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 修复 O14：为 {@link ItineraryServiceImpl} 的 AI 文本抽取正则补单元测试。
 *
 * <p>这些抽取方法只依赖静态 {@link java.util.regex.Pattern}，不触碰 Mapper / 外部服务，
 * 属于可测性最高的纯逻辑。通过反射直接调用私有方法，避免为了测试而放宽生产代码可见性。</p>
 */
class ItineraryServiceImplExtractionTest {

    /** 抽取方法不使用任何 Mapper，构造时注入 null 即可 */
    private final ItineraryServiceImpl service = new ItineraryServiceImpl(null, null, null);

    @SuppressWarnings("unchecked")
    private <T> T invoke(String methodName, Object... args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = String.class;
        }
        try {
            Method method = ItineraryServiceImpl.class.getDeclaredMethod(methodName, types);
            method.setAccessible(true);
            return (T) method.invoke(service, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("反射调用失败: " + methodName, e);
        }
    }

    @Test
    void extractDatesSupportsDashSlashDotAndChineseSeparators() {
        assertEquals(List.of(LocalDate.of(2026, 5, 1)),
                this.<List<LocalDate>>invoke("extractDates", "2026-05-01 出发"));
        assertEquals(List.of(LocalDate.of(2026, 5, 1)),
                this.<List<LocalDate>>invoke("extractDates", "2026/5/1"));
        assertEquals(List.of(LocalDate.of(2026, 5, 1)),
                this.<List<LocalDate>>invoke("extractDates", "2026.5.1"));
        assertEquals(List.of(LocalDate.of(2026, 5, 1)),
                this.<List<LocalDate>>invoke("extractDates", "2026年5月1日"));
    }

    @Test
    void extractDatesKeepsAtMostTwoAndSkipsInvalid() {
        List<LocalDate> two = invoke("extractDates", "2026-05-01 至 2026-05-05 再 2026-06-01");
        assertEquals(2, two.size());
        assertEquals(LocalDate.of(2026, 5, 1), two.get(0));
        assertEquals(LocalDate.of(2026, 5, 5), two.get(1));

        List<LocalDate> invalid = invoke("extractDates", "2026-13-45");
        assertTrue(invalid.isEmpty());
    }

    @Test
    void extractDatesHandlesNullOrBlank() {
        assertTrue(this.<List<LocalDate>>invoke("extractDates", (Object) null).isEmpty());
        assertTrue(this.<List<LocalDate>>invoke("extractDates", "   ").isEmpty());
    }

    @Test
    void extractDaysDefaultsToTwoWhenAbsent() {
        assertEquals(5, this.<Integer>invoke("extractDays", "北京 5天4晚"));
        assertEquals(2, this.<Integer>invoke("extractDays", "没有天数描述"));
        assertEquals(2, this.<Integer>invoke("extractDays", (Object) null));
    }

    @Test
    void extractTravelersParsesPersonAndWei() {
        assertEquals(3, this.<Integer>invoke("extractTravelers", "3人同行"));
        assertEquals(2, this.<Integer>invoke("extractTravelers", "2位成人"));
        assertNull(this.<Integer>invoke("extractTravelers", "无人数描述"));
        assertNull(this.<Integer>invoke("extractTravelers", (Object) null));
    }

    @Test
    void extractDestinationMatchesHintWords() {
        assertEquals("北京", this.<String>invoke("extractDestination", "我想去北京"));
        assertEquals("东京", this.<String>invoke("extractDestination", "前往东京"));
        assertNull(this.<String>invoke("extractDestination", "随便走走"));
        assertNull(this.<String>invoke("extractDestination", (Object) null));
    }

    @Test
    void extractDestinationFallsBackToItineraryLine() {
        assertEquals("北京三日游", this.<String>invoke("extractDestination", "北京三日游行程\n其他内容"));
    }
}
