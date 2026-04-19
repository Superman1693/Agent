package com.ahead.aiagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件存储的AI会话历史记录器
 */
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;
    private final Kryo kryo = new Kryo();

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
        //获取已有消息
        List<Message> messageList = getOrCreateConversationMessages(conversationId);
        //添加新消息
        messageList.add(message);
        saveConversationMessages(conversationId, messageList);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> messageList = getOrCreateConversationMessages(conversationId);
        messageList.addAll(messages);//批量添加
        saveConversationMessages(conversationId, messageList);
    }


    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversationMessages(conversationId);
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
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
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
                e.printStackTrace();
            }
        }
        return new ArrayList<>();
    }

    /**
     * 保存会话消息列表
     */
    private void saveConversationMessages(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messages);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }
}