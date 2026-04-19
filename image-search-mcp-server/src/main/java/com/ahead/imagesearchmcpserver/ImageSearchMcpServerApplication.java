package com.ahead.imagesearchmcpserver;

import com.ahead.imagesearchmcpserver.tools.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ImageSearchMcpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ImageSearchMcpServerApplication.class, args);
	}


	@Bean
	public ToolCallbackProvider imagesSearchTools(ImageSearchTool imageSearchTool){
		return MethodToolCallbackProvider.builder()
				.toolObjects(imageSearchTool)
				.build();
	}
}
