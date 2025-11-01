package org.egg.docagent.splitter;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class TextSplitterTest {
    public static void main(String[] args) throws Exception{
        String path = "D:/doc_out/D__doc_cic_河图云枢院_架构图_2023.02.28-综合履约平台应用架构0.1.pptx.txt";

        List<String> list = Files.readAllLines(Paths.get(path));

        StringBuilder sb = new StringBuilder();

        String id = list.get(0);
        String fileName = list.get(1);
        String createDt = list.get(2);
        String updateDt = list.get(3);

        for (int i=4; i<list.size(); i++) {
            sb.append(list.get(i));
        }

        System.out.println(sb.length());

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(8192)
                .build();

        Document document = Document.builder().id(id).text(sb.toString()).build();
        List<Document> documents = splitter.apply(List.of(document));
        documents.forEach(doc -> {
            System.out.println(doc.getText());
            System.out.println("========================"+doc.getText().length()+"==========================");
        });

    }
}
