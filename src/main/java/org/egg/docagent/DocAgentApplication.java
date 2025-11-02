package org.egg.docagent;

import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {OllamaEmbeddingAutoConfiguration.class, OpenAiChatAutoConfiguration.class})
public class DocAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocAgentApplication.class, args);
    }

}
