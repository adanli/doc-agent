package org.egg.docagent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingModelConfig {

    @Bean("ollamaEmbeddingModel")
    @Primary  // 标记这个为首选 bean
    public EmbeddingModel ollamaEmbeddingModel() {

        // 你的 OpenAI Embedding 配置
        return OllamaEmbeddingModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl("http://localhost:11434")
                        .build())
                .defaultOptions(OllamaOptions.builder()
                        .model("m3e:latest")
                        .build())
                .build();
    }

}