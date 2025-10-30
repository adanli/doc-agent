package org.egg.docagent.ppt2image;

import org.apache.poi.hslf.usermodel.*;
import org.apache.poi.xslf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PPTToImageConverter {
    private static final double SCALE = 2.0; // 图片缩放因子，提高分辨率

    /**
     * 将PPT文件转换为图片
     * @param pptFilePath PPT文件路径
     * @param outputDir 输出目录
     * @param format 图片格式（png, jpg等）
     * @return 生成的图片文件路径列表
     */
    public static List<String> convertToImages(String pptFilePath, String outputDir, String format) {
        List<String> imagePaths = new ArrayList<>();

        try {
            File pptFile = new File(pptFilePath);
            if (!pptFile.exists()) {
                throw new FileNotFoundException("PPT文件不存在: " + pptFilePath);
            }

            String fileName = pptFile.getName().toLowerCase();
            if (fileName.endsWith(".pptx")) {
                imagePaths = convertPPTXToImages(pptFile, outputDir, format);
            } else if (fileName.endsWith(".ppt")) {
                imagePaths = convertPPTToImages(pptFile, outputDir, format);
            } else {
                throw new IllegalArgumentException("不支持的文件格式: " + fileName);
            }

        } catch (Exception e) {
            throw new RuntimeException("转换PPT到图片失败", e);
        }

        return imagePaths;
    }

    /**
     * 转换PPTX文件为图片
     */
    private static List<String> convertPPTXToImages(File pptFile, String outputDir, String format)
            throws IOException {
        List<String> imagePaths = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(pptFile);
             XMLSlideShow ppt = new XMLSlideShow(fis)) {

            // 设置幻灯片尺寸
            Dimension pageSize = ppt.getPageSize();
            int width = (int) (pageSize.width * SCALE);
            int height = (int) (pageSize.height * SCALE);

            List<XSLFSlide> slides = ppt.getSlides();
            System.out.println("开始转换PPTX，共 " + slides.size() + " 张幻灯片");

            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                String imagePath = saveSlideAsImage(slide, width, height, outputDir, format, i + 1);
                imagePaths.add(imagePath);
                System.out.println("已转换第 " + (i + 1) + " 张幻灯片: " + imagePath);
            }
        }

        return imagePaths;
    }

    /**
     * 转换PPT文件为图片
     */
    private static List<String> convertPPTToImages(File pptFile, String outputDir, String format)
            throws IOException {
        List<String> imagePaths = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(pptFile);
             HSLFSlideShow ppt = new HSLFSlideShow(fis)) {

            // 设置幻灯片尺寸
            Dimension pageSize = ppt.getPageSize();
            int width = (int) (pageSize.width * SCALE);
            int height = (int) (pageSize.height * SCALE);

            List<HSLFSlide> slides = ppt.getSlides();
            System.out.println("开始转换PPT，共 " + slides.size() + " 张幻灯片");

            for (int i = 0; i < slides.size(); i++) {
                HSLFSlide slide = slides.get(i);
                String imagePath = saveSlideAsImage(slide, width, height, outputDir, format, i + 1);
                imagePaths.add(imagePath);
                System.out.println("已转换第 " + (i + 1) + " 张幻灯片: " + imagePath);
            }
        }

        return imagePaths;
    }

    /**
     * 保存PPTX幻灯片为图片
     */
    private static String saveSlideAsImage(XSLFSlide slide, int width, int height,
                                         String outputDir, String format, int slideNumber) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();

            // 设置渲染参数
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            // 缩放
            graphics.setTransform(AffineTransform.getScaleInstance(SCALE, SCALE));

            // 设置背景色为白色
            graphics.setColor(Color.WHITE);
            graphics.fill(new Rectangle(0, 0, width, height));

            String fileName = String.format("slide_%03d.%s", slideNumber, format);

            // 绘制幻灯片内容
            try {
                slide.draw(graphics);
            } catch (Exception e) {
                System.err.println("绘制幻灯片存在问题: " + String.format("%s/%s", outputDir, fileName));
            }

            // 保存图片
            File outputFile = new File(outputDir, fileName);
            ImageIO.write(img, format, outputFile);

            graphics.dispose();

            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("保存幻灯片图片失败: " + slideNumber, e);
        }
    }

    /**
     * 保存PPT幻灯片为图片
     */
    private static String saveSlideAsImage(HSLFSlide slide, int width, int height,
                                         String outputDir, String format, int slideNumber) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = img.createGraphics();

            // 设置渲染参数
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 缩放
            graphics.setTransform(AffineTransform.getScaleInstance(SCALE, SCALE));

            // 设置背景色为白色
            graphics.setColor(Color.WHITE);
            graphics.fill(new Rectangle(0, 0, width, height));

            // 绘制幻灯片内容
            slide.draw(graphics);

            // 保存图片
            String fileName = String.format("slide_%03d.%s", slideNumber, format);
            File outputFile = new File(outputDir, fileName);
            ImageIO.write(img, format, outputFile);

            graphics.dispose();

            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("保存幻灯片图片失败: " + slideNumber, e);
        }
    }

    /**
     * 通过PDF中转转换PPT（更高质量，但需要额外步骤）
     */
    public static List<String> convertViaPDF(String pptFilePath, String outputDir, String format) {
        // 注意：此方法需要先将PPT转换为PDF，再将PDF转换为图片
        // 这里只提供思路，实际实现可能需要其他库或工具
        throw new UnsupportedOperationException("PDF中转转换暂未实现");
    }

    /**
     * 批量转换多个PPT文件
     */
    public static void batchConvert(List<String> pptFilePaths, String outputDir, String format) {
        for (String pptFilePath : pptFilePaths) {
            try {
                System.out.println("开始转换文件: " + pptFilePath);
                List<String> imagePaths = convertToImages(pptFilePath, outputDir, format);
                System.out.println("转换完成，生成 " + imagePaths.size() + " 张图片");
            } catch (Exception e) {
                System.err.println("转换文件失败: " + pptFilePath + ", 错误: " + e.getMessage());
            }
        }
    }
}
