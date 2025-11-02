package org.egg.docagent.config;

import org.springframework.ai.chat.client.ChatClient;
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

    /*@Bean("openAiChatModel")
    public ChatModel openAiChatModel() {
        return new OpenAiChatModel();
    }*/
}
