package org.egg.docagent.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DeleteFile {

    public static void main(String[] args) throws Exception{
        String path = "/Users/adan/doc_out_ollama";
        File dir = new File(path);

        List<String> deleteFiles = new ArrayList<>();

        String[] files = dir.list();
        for (String f: files) {
            String file = String.format("%s/%s", dir, f);
            if(!file.endsWith(".txt")) continue;
            File file1 = new File(file);

            try {
                List<String> list = Files.readAllLines(Paths.get(file));
                if(list.size() < 4) {
                    deleteFiles.add(file);
                    continue;
                }

                String str = list.get(4);
                if(
                        str.trim().isEmpty()
                                || str.trim().equals("!")
                                || str.trim().equals("文档内容为空，无法进行提炼。")
                                || str.trim().equals("null")
                ) {
                    deleteFiles.add(file);
                    continue;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        deleteFiles.forEach(f -> {
            File file = new File(f);
            System.out.printf("%s file is delete: %s%n", f, file.delete());
        });

    }

}
