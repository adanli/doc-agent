package org.egg.docagent.pdf2image;

import java.io.File;
import java.util.List;

public class PPTToImageExample {
    public static void main(String[] args) {
        // 示例1：基本转换
        convertSingleFile();

        // 示例2：批量转换
        // batchConvertFiles();

        // 示例3：自定义配置转换
        // convertWithCustomConfig();
    }

    /**
     * 示例1：转换单个PPT文件
     */
    public static void convertSingleFile() {
        try {
            String pptFilePath = "D:\\doc\\cic\\政健险补充高阶方案0.1(1).pptx"; // 或 "presentation.ppt"
//            String pptFilePath = "/Users/adan/Documents/1. 工作/1. 中华/3. 架构办/5. 工作计划/2025年/1. 全年/2025年务虚会_架构办.pptx"; // 或 "presentation.ppt"
            String outputDir = "output_images";
            String format = "png";

            // 创建输出目录
            File outputDirectory = new File(outputDir);
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            // 执行转换
            List<String> imagePaths = PPTToImageConverter.convertToImages(
                pptFilePath, outputDir, format);

            System.out.println("转换完成！生成图片列表：");
            for (String imagePath : imagePaths) {
                System.out.println("  - " + imagePath);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 示例2：批量转换多个文件
     */
    public static void batchConvertFiles() {
        String outputDir = "batch_output";
        String format = "jpg";

        // 创建输出目录
        new File(outputDir).mkdirs();

        // PPT文件列表
        List<String> pptFiles = List.of(
            "presentation1.pptx",
            "presentation2.ppt",
            "presentation3.pptx"
        );

        // 批量转换
        PPTToImageConverter.batchConvert(pptFiles, outputDir, format);
    }

    /**
     * 示例3：使用自定义配置
     */
    public static void convertWithCustomConfig() {
        try {
            ConversionConfig config = new ConversionConfig();
            config.setOutputFormat("jpg");
            config.setScale(1.5);

            String pptFilePath = "presentation.pptx";
            String outputDir = "custom_output";

            new File(outputDir).mkdirs();

            // 在实际应用中，您需要修改PPTToImageConverter以支持配置参数
            List<String> imagePaths = PPTToImageConverter.convertToImages(
                pptFilePath, outputDir, config.getOutputFormat());

            System.out.println("自定义配置转换完成！");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
