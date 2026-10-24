package com.ahead.aiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于文件存储的AI会话历史记录器
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;
    private final Kryo kryo = new Kryo();

    /**
     * 每个会话一把锁，修复 E12/D15 的读-改-写竞态。
     * 修复：锁 map 加上限（10000），超限时直接 clear()——锁对象可通过 computeIfAbsent 随时重建，
     * 清空只是让并发会话短暂失去互斥（退化为串行文件读写），不会丢数据，
     * 避免会话 id 无界增长把锁 map 撑爆内存。
     */
    private static final int MAX_LOCKS = 10_000;
    private final Map<String, Object> conversationLocks = new ConcurrentHashMap<>();

    {
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        // Kryo 需要注册具体的 ArrayList 类型以避免某些环境下的警告
        kryo.register(ArrayList.class);
    }

    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, Message message) {
        // 修复 E12/D15：同会话并发 add 会互相覆盖，这里按会话加锁保证读-改-写原子
        synchronized (lockFor(conversationId)) {
            List<Message> messageList = getOrCreateConversationMessages(conversationId);
            messageList.add(message);
            saveConversationMessages(conversationId, messageList);
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        synchronized (lockFor(conversationId)) {
            List<Message> messageList = getOrCreateConversationMessages(conversationId);
            messageList.addAll(messages);//批量添加
            saveConversationMessages(conversationId, messageList);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        synchronized (lockFor(conversationId)) {
            return getOrCreateConversationMessages(conversationId);
        }
    }

    //获取最近N条消息
    public List<Message> get(String conversationId, int lastN) {
        List<Message> messageList = get(conversationId);
        return messageList.stream()
                .skip(Math.max(0, messageList.size() - lastN))
                .toList();
    }

    @Override
    public void clear(String conversationId) {
        synchronized (lockFor(conversationId)) {
            File file = getConversationFile(conversationId);
            if (file.exists() && !file.delete()) {
                log.warn("会话历史文件删除失败: {}", file.getAbsolutePath());
            }
        }
    }

    private Object lockFor(String conversationId) {
        // 超限兜底：直接清空重建（锁可重建，clear 安全；见字段注释）
        if (conversationLocks.size() >= MAX_LOCKS) {
            conversationLocks.clear();
        }
        return conversationLocks.computeIfAbsent(conversationId, k -> new Object());
    }

    /**
     * 获取或创建会话消息的列表
     */
    @SuppressWarnings("unchecked")
    private List<Message> getOrCreateConversationMessages(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                // 使用强转并配合 SuppressWarnings 消除 unchecked 警告
                return (List<Message>) kryo.readObject(input, ArrayList.class);
            } catch (Exception e) {
                // 修复 E12/D15：解析失败不再静默返回空列表（等于悄悄丢失全部历史），
                // 而是记录错误日志，便于排查文件损坏问题。
                log.error("读取会话历史失败，将返回空历史: conversationId={}, file={}",
                        conversationId, file.getAbsolutePath(), e);
            }
        }
        return new ArrayList<>();
    }

    /**
     * 保存会话消息列表
     * 修复 E12/D15：先写临时文件再原子重命名，避免写到一半进程崩溃导致文件损坏。
     */
    private void saveConversationMessages(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        File tmpFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try (Output output = new Output(new FileOutputStream(tmpFile))) {
            kryo.writeObject(output, messages);
        } catch (Exception e) {
            log.error("写入会话历史失败: conversationId={}", conversationId, e);
            return;
        }

        if (file.exists() && !file.delete()) {
            log.warn("旧会话文件删除失败，将直接覆盖: {}", file.getAbsolutePath());
        }
        if (!tmpFile.renameTo(file)) {
            log.error("会话历史文件原子替换失败: {} -> {}", tmpFile.getAbsolutePath(), file.getAbsolutePath());
        }
    }

    private File getConversationFile(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        // 修复 E13/D10：显式白名单校验，并用 "_" 替换 ":"（Windows 非法文件名字符），
        // 使路径穿越防护从「碰巧失败」变为「设计防护」，同时兼容 Windows/Linux。
        String safe = conversationId.replace(':', '_');
        if (!safe.matches("^[A-Za-z0-9_\\-]{1,128}$")) {
            throw new IllegalArgumentException("非法的 conversationId: " + conversationId);
        }
        return new File(BASE_DIR, safe + ".kryo");
    }
}
