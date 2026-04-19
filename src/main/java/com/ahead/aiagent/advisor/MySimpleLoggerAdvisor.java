package com.ahead.aiagent.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * AI调用日志打印器--同步调用和流式调用
 */
@Component("mySimpleLoggerAdvisor")
public class MySimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	private static final Logger logger = LoggerFactory.getLogger(MySimpleLoggerAdvisor.class);

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 0;
	}


	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		// 打印请求日志
		logRequest(chatClientRequest);
		//	给下一个拦截器进行处理
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
		// 打印响应日志
		logResponse(chatClientResponse);
		// 返回响应
		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
												 StreamAdvisorChain streamAdvisorChain) {
		logRequest(chatClientRequest);

		Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

		return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse);
	}

	private void logRequest(ChatClientRequest request) {
		logger.debug("request: {}", request);
	}

	private void logResponse(ChatClientResponse chatClientResponse) {
		logger.debug("response: {}", chatClientResponse);
	}

}