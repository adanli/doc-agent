package org.egg.docagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
public class Config {
    @Bean
    @Primary
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("You are a friendly chat bot that answers question in the voice of a {voice}")
                .build();
    }

    /*@Bean("ollamaChatModel")
    @Primary
    public ChatModel ollamaChatModel() {
        return OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl("http://localhost:11434")
                        .build())
                .defaultOptions(OllamaOptions.builder()
                        .model("qwen3-vl:8b")
                        .mainGPU(1)
                        .numGPU(100)
                        .build())
                .build();
    }*/

    /*@Bean("openAiChatModel")
    public ChatModel openAiChatModel() {
        return new OpenAiChatModel();
    }*/
}
