package com.ahead.aiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 自定义Advisor,让AI再读一遍问题，提升理解准确性
 */
@Slf4j
public class ReReadingAdvisor implements BaseAdvisor {

	// 默认的模板--强制AI仔细阅读问题
	private static final String DEFAULT_RE2_ADVISE_TEMPLATE = """
            {re2_input_query}
            Read the question again: {re2_input_query}
            """;

	private final String re2AdviseTemplate;
	//拦截器的执行顺序（数字越小越先执行）
	private int order = 0;

	public ReReadingAdvisor() {
		this(DEFAULT_RE2_ADVISE_TEMPLATE);
	}

	public ReReadingAdvisor(String re2AdviseTemplate) {
		this.re2AdviseTemplate = re2AdviseTemplate;
	}

	// 请求发送前拦截
	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		// 提取原始的用户输入文本
		String originalText = request.prompt().getInstructions().stream()
				.filter(m -> m.getMessageType() == MessageType.USER)
				.map(Message::getText)
				.reduce((a, b) -> a + "\n" + b)
				.orElse("");

		PromptTemplate promptTemplate = new PromptTemplate(this.re2AdviseTemplate);
		String augmentedUserText = promptTemplate.render(Map.of("re2_input_query", originalText));

		// 重构 Prompt 中的 UserMessage
		List<Message> newMessages = new ArrayList<>();
		for (Message msg : request.prompt().getInstructions()) {
			if (msg.getMessageType() == MessageType.USER) {
				newMessages.add(new UserMessage(augmentedUserText));
			} else {
				newMessages.add(msg);
			}
		}

		// 构建并返回变异后的 ChatClientRequest
		return request.mutate()
				.prompt(new Prompt(newMessages))
				.build();
	}


	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		try{
			String answer=response.chatResponse().getResult().getOutput().getText();
			//需要添加后缀-前缀-提示-版权等
//		String newAnswer = answer + "\n\n—— 由智能助手提供";
//
//		return response.mutate()
//				.result(result -> result.output().text(newAnswer))
//				.build();

			log.info("answer: {}", answer);
			return response;
		}
		catch (Exception e){
			log.error("error: {}", e.getMessage());
			return response;
		}

	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public ReReadingAdvisor withOrder(int order) {
		this.order = order;
		return this;
		//链式调用使用方法
		// 链式创建 Advisor
//		var reReadingAdvisor = new ReReadingAdvisor().withOrder(1);
//
// 		加入 ChatClient
//		ChatClient chatClient = ChatClient.builder(model)
//				.defaultAdvisors(reReadingAdvisor)
//				.build();
	}
}