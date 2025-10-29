package org.egg.docagent.pdf2image;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFToImageConverter {

    public static List<String> convertToImages(String pdfFilePath, String outputDir) {
        return convertToImages(pdfFilePath, outputDir, "png", 150);
    }
    
    /**
     * 将PDF文件转换为图片
     * @param pdfFilePath PDF文件路径
     * @param outputDir 输出目录
     * @param format 图片格式（png, jpg, jpeg, bmp等）
     * @param dpi 图片分辨率（默认150）
     * @return 生成的图片文件路径列表
     */
    public static List<String> convertToImages(String pdfFilePath, String outputDir, 
                                             String format, int dpi) {
        List<String> imagePaths = new ArrayList<>();
        
        File pdfFile = new File(pdfFilePath);
        if (!pdfFile.exists()) {
            throw new IllegalArgumentException("PDF文件不存在: " + pdfFilePath);
        }
        
        // 创建输出目录
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            
            int pageCount = document.getNumberOfPages();
            System.out.println("开始转换PDF: " + pdfFile.getName());
            System.out.println("总页数: " + pageCount);
            System.out.println("输出格式: " + format);
            System.out.println("分辨率: " + dpi + " DPI");
            
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                try {
                    // 渲染PDF页面为图片
                    System.out.println("正在转换第 " + (pageIndex + 1) + " 页...");
                    BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, dpi);
                    
                    // 生成输出文件名
                    String imageName = generateImageName(pdfFile.getName(), pageIndex + 1, format);
                    File imageFile = new File(outputDir, imageName);
                    if(!imageFile.exists()) {
                        imageFile.createNewFile();
                    }

                    // 保存图片
                    boolean success = ImageIOUtil.writeImage(image, format, new FileOutputStream(imageFile), dpi);
                    
                    if (success) {
                        imagePaths.add(imageFile.getAbsolutePath());
                        System.out.println("✓ 第 " + (pageIndex + 1) + " 页转换成功: " + imageFile.getName());
                    } else {
                        System.err.println("✗ 第 " + (pageIndex + 1) + " 页转换失败");
                    }
                    
                } catch (Exception e) {
                    System.err.println("转换第 " + (pageIndex + 1) + " 页时发生错误: " + e.getMessage());
                }
            }
            
            System.out.println("转换完成！共生成 " + imagePaths.size() + " 张图片");
            
        } catch (IOException e) {
            throw new RuntimeException("PDF文件加载失败: " + e.getMessage(), e);
        }
        
        return imagePaths;
    }
    
    /**
     * 简化方法 - 使用默认DPI
     */
    public static List<String> convertToImages(String pdfFilePath, String outputDir, String format) {
        return convertToImages(pdfFilePath, outputDir, format, 150);
    }
    
    /**
     * 生成图片文件名
     */
    private static String generateImageName(String pdfFileName, int pageNumber, String format) {
        // 移除.pdf扩展名
        String baseName = pdfFileName.replace(".pdf", "").replace(".PDF", "");
        return String.format("%s_page_%03d.%s", baseName, pageNumber, format.toLowerCase());
    }
    
    /**
     * 高质量转换（300 DPI）
     */
    public static List<String> convertToHighQualityImages(String pdfFilePath, String outputDir, String format) {
        return convertToImages(pdfFilePath, outputDir, format, 300);
    }
    
    /**
     * 快速转换（72 DPI）
     */
    public static List<String> convertToFastImages(String pdfFilePath, String outputDir, String format) {
        return convertToImages(pdfFilePath, outputDir, format, 72);
    }
    
    /**
     * 转换指定页面范围
     */
    public static List<String> convertPageRange(String pdfFilePath, String outputDir, 
                                              String format, int dpi, int startPage, int endPage) {
        List<String> imagePaths = new ArrayList<>();
        
        File pdfFile = new File(pdfFilePath);
        if (!pdfFile.exists()) {
            throw new IllegalArgumentException("PDF文件不存在: " + pdfFilePath);
        }
        
        // 创建输出目录
        File outputDirectory = new File(outputDir);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }
        
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            
            int pageCount = document.getNumberOfPages();
            
            // 验证页面范围
            if (startPage < 1 || endPage > pageCount || startPage > endPage) {
                throw new IllegalArgumentException("无效的页面范围: " + startPage + " - " + endPage + 
                                                 " (总页数: " + pageCount + ")");
            }
            
            System.out.println("转换页面范围: " + startPage + " - " + endPage);
            
            for (int pageIndex = startPage - 1; pageIndex < endPage; pageIndex++) {
                try {
                    System.out.println("正在转换第 " + (pageIndex + 1) + " 页...");
                    BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, dpi);
                    
                    String imageName = generateImageName(pdfFile.getName(), pageIndex + 1, format);
                    File imageFile = new File(outputDir, imageName);

                    if(!imageFile.exists()) imageFile.createNewFile();
                    
                    boolean success = ImageIOUtil.writeImage(image, format, new FileOutputStream(imageFile), dpi);
                    
                    if (success) {
                        imagePaths.add(imageFile.getAbsolutePath());
                        System.out.println("✓ 第 " + (pageIndex + 1) + " 页转换成功");
                    } else {
                        System.err.println("✗ 第 " + (pageIndex + 1) + " 页转换失败");
                    }
                    
                } catch (Exception e) {
                    System.err.println("转换第 " + (pageIndex + 1) + " 页时发生错误: " + e.getMessage());
                }
            }
            
        } catch (IOException e) {
            throw new RuntimeException("PDF文件加载失败: " + e.getMessage(), e);
        }
        
        return imagePaths;
    }
}