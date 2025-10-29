package org.egg.docagent.word2image;

import java.io.File;
import java.util.List;

public class WordToImageExample {
    public static void main(String[] args) {
        try {
            String wordFilePath = "/Users/adan/Documents/1. 工作/1. 中华/3. 架构办/1. 管理办法/4. 评审管理办法/6. 技改评审/技改方案的评审管理办法.docx";
            String outputDir = "converted_images";
            String format = "png";

            // 创建输出目录
            new File(outputDir).mkdirs();

            System.out.println("开始转换Word文档: " + wordFilePath);

            // 检查LibreOffice是否可用
            if (WordToImageConverter.isLibreOfficeAvailable()) {
                System.out.println("使用LibreOffice进行高质量转换（保留所有图片和格式）");
                List<String> imagePaths = WordToImageConverter.convertWithLibreOffice(
                    wordFilePath, outputDir, format);

                System.out.println("转换完成！生成 " + imagePaths.size() + " 张图片");
            } else {
                System.out.println("使用POI进行转换（提取文本和图片）");
                List<String> imagePaths = WordToImageConverter.convertWithPoi(
                    wordFilePath, outputDir, format);

                System.out.println("转换完成！生成 " + imagePaths.size() + " 张图片");
            }

        } catch (Exception e) {
            System.err.println("转换过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
