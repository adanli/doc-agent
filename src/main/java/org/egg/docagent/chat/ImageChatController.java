package org.egg.docagent.chat;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;

import java.io.FileInputStream;
import java.io.InputStream;

public class ImageChatController {

    public static void main(String[] args) throws Exception{
        String path = "D:\\doc_out\\D__doc_test_1.pdf_dir\\1_page_001.png";

        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(OllamaApi.builder()
                        .baseUrl("http://localhost:11434")
                        .build())
                .defaultOptions(OllamaOptions.builder()
                        .numGPU(100)
                        .mainGPU(1)
                        .model("qwen3-vl:8b")
                        .build())
                .build();

        InputStream inputStream = new FileInputStream(path);

        // 将上传的图像转换为 Media 对象
        Media imageMedia = new Media(
                MediaType.APPLICATION_XML,
//            new ByteArrayResource(image.getBytes())
            new InputStreamResource(inputStream)
        );


        // 构建多模态消息
        UserMessage userMessage = UserMessage.builder()
                .text("""
                        任务要求：
                            请仔细阅读以下文档内容，并执行以下操作：
            
                            保留原始语义：确保提取的内容忠实反映原文核心信息，不添加主观解释或外部知识。
                            高度浓缩：去除重复、格式符号、页眉页脚、无关修饰语等非实质内容，仅保留对理解文档主题、关键事实、实体和逻辑关系有贡献的文本。
                            结构化输出（可选但推荐）：若文档包含明确结构（如标题、章节、列表、表格），请用简洁的自然语言将其逻辑关系保留下来（例如：“第一章：引言——介绍研究背景与目标”）。
                            输出纯文本：不要使用 Markdown、XML 或其他标记语言，仅输出干净、连贯的中文（或原文语言）段落。
                            长度控制：总输出长度应控制在150字以内，优先保留高频关键词、专有名词、数据、结论和行动项。
                            输出格式：
                            直接输出提炼后的文本内容，不要包含任何解释、前缀（如“提炼结果：”）或后缀。
                        """)
                .media(imageMedia)
                .build();

        Prompt prompt = new Prompt(userMessage);
        ChatResponse response = chatModel.call(prompt);

//        String response = chatModel.call("今天星期几");

        System.out.println(response.getResult().getOutput().getText());
    }

}
