package org.egg.docagent.util;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 图片压缩工具类
 */
public class ImageCompressor {

    /**
     * 使用Thumbnailator压缩图片
     *
     * @param inputStream 输入流
     * @param format 图片格式 (jpg, png等)
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @param quality 压缩质量 (0.0 - 1.0)
     * @return 压缩后的图片字节数组
     */
    public static byte[] compressWithThumbnailator(InputStream inputStream,
                                                  String format,
                                                  int maxWidth,
                                                  int maxHeight,
                                                  double quality) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(inputStream)
                .size(maxWidth, maxHeight)
                .outputFormat(format)
                .outputQuality(quality)
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * 使用ImgScalr压缩图片
     *
     * @param inputStream 输入流
     * @param format 图片格式
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @return 压缩后的图片字节数组
     */
    public static byte[] compressWithImgScalr(InputStream inputStream,
                                             String format,
                                             int maxWidth,
                                             int maxHeight) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputStream);

        // 计算缩放比例
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        int targetWidth = originalWidth;
        int targetHeight = originalHeight;

        if (originalWidth > maxWidth || originalHeight > maxHeight) {
            // 按比例缩放
            double widthRatio = (double) maxWidth / originalWidth;
            double heightRatio = (double) maxHeight / originalHeight;
            double ratio = Math.min(widthRatio, heightRatio);

            targetWidth = (int) (originalWidth * ratio);
            targetHeight = (int) (originalHeight * ratio);
        }

        // 使用ImgScalr进行高质量缩放
        BufferedImage scaledImage = Scalr.resize(originalImage,
                Scalr.Method.QUALITY,
                Scalr.Mode.AUTOMATIC,
                targetWidth, targetHeight);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, format, outputStream);

        return outputStream.toByteArray();
    }

    /**
     * 保持宽高比压缩图片
     *
     * @param inputStream 输入流
     * @param format 图片格式
     * @param maxSize 最大尺寸（宽或高）
     * @param quality 压缩质量
     * @return 压缩后的图片字节数组
     */
    public static byte[] compressProportional(InputStream inputStream,
                                             String format,
                                             int maxSize,
                                             double quality) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(inputStream)
                .size(maxSize, maxSize)
                .outputFormat(format)
                .outputQuality(quality)
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * 压缩并添加水印
     *
     * @param inputStream 输入流
     * @param format 图片格式
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @param quality 压缩质量
     * @param watermarkText 水印文字
     * @return 压缩后的图片字节数组
     */
    public static byte[] compressWithWatermark(InputStream inputStream,
                                              String format,
                                              int maxWidth,
                                              int maxHeight,
                                              double quality,
                                              String watermarkText) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Thumbnails.of(inputStream)
                .size(maxWidth, maxHeight)
                .watermark(Positions.BOTTOM_RIGHT, createWatermark(watermarkText), 0.5f)
                .outputFormat(format)
                .outputQuality(quality)
                .toOutputStream(outputStream);

        return outputStream.toByteArray();
    }

    /**
     * 创建水印图片
     */
    private static BufferedImage createWatermark(String text) {
        BufferedImage watermark = new BufferedImage(200, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = watermark.createGraphics();

        // 设置抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 设置半透明
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

        // 设置字体和颜色
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.setColor(Color.WHITE);

        // 绘制文字
        g2d.drawString(text, 10, 30);
        g2d.dispose();

        return watermark;
    }

    /**
     * 获取图片格式
     */
    public static String getImageFormat(String fileName) {
        if (fileName == null) {
            return "jpg";
        }

        String lowerFileName = fileName.toLowerCase();
        if (lowerFileName.endsWith(".png")) {
            return "png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "gif";
        } else if (lowerFileName.endsWith(".bmp")) {
            return "bmp";
        } else {
            return "jpg";
        }
    }
}