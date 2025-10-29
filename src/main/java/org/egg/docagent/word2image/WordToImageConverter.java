package org.egg.docagent.word2image;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WordToImageConverter {

    private static final float DPI = 150;
    private static final int PAGE_WIDTH = 1080;
    private static final int PAGE_HEIGHT = 1920;
    private static final int MARGIN = 50;

    /**
     * 将Word文档转换为图片（保留图片内容）
     */
    public static List<String> convertToImages(String wordFilePath, String outputDir, String format) {
        // 优先使用LibreOffice转换（能最好地保留图片和格式）
        if (isLibreOfficeAvailable()) {
            return convertWithLibreOffice(wordFilePath, outputDir, format);
        } else {
            // 备用方案：使用POI提取内容并生成图片
            return convertWithPoi(wordFilePath, outputDir, format);
        }
    }

    /**
     * 使用LibreOffice转换（最佳方案）
     */
    public static List<String> convertWithLibreOffice(String wordFilePath, String outputDir, String format) {
        try {
            // 第一步：转换为PDF
            File pdfFile = convertWordToPdfWithLibreOffice(wordFilePath, outputDir);

            // 第二步：PDF转图片
            List<String> imagePaths = convertPdfToImages(pdfFile, outputDir, format);

            // 清理临时文件
            if (pdfFile.exists()) {
                pdfFile.delete();
            }

            return imagePaths;

        } catch (Exception e) {
            throw new RuntimeException("LibreOffice转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用POI提取内容并生成图片
     */
    public static List<String> convertWithPoi(String wordFilePath, String outputDir, String format) {
        List<String> imagePaths = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(wordFilePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            // 创建输出目录
            new File(outputDir).mkdirs();

            // 提取文档中的所有图片
            Map<String, BufferedImage> images = extractImagesFromDocument(document, outputDir);

            // 生成页面内容
            List<PageContent> pages = parseDocumentContent(document, images);

            // 生成图片
            for (int i = 0; i < pages.size(); i++) {
                String imagePath = renderPageToImage(pages.get(i), outputDir,
                                                   String.format("page_%03d.%s", i + 1, format));
                imagePaths.add(imagePath);
                System.out.println("生成第 " + (i + 1) + " 页: " + imagePath);
            }

        } catch (Exception e) {
            throw new RuntimeException("POI转换失败: " + e.getMessage(), e);
        }

        return imagePaths;
    }

    /**
     * 从Word文档中提取所有图片
     */
    private static Map<String, BufferedImage> extractImagesFromDocument(XWPFDocument document, String outputDir) {
        Map<String, BufferedImage> images = new HashMap<>();

        try {
            // 提取嵌入图片
            List<XWPFPictureData> allPictures = document.getAllPictures();

            for (XWPFPictureData picture : allPictures) {
                try {
                    byte[] data = picture.getData();
                    String imageKey = "img_" + System.currentTimeMillis() + "_" + images.size();

                    // 将图片数据转换为BufferedImage
                    ByteArrayInputStream bis = new ByteArrayInputStream(data);
                    BufferedImage bufferedImage = ImageIO.read(bis);
                    bis.close();

                    if (bufferedImage != null) {
                        images.put(imageKey, bufferedImage);

                        // 可选：保存图片到文件用于调试
//                        File debugImage = new File(outputDir, imageKey + "." + picture.getPictureType().extension);
//                        ImageIO.write(bufferedImage, picture.getPictureType().extension, debugImage);
                    }

                } catch (Exception e) {
                    System.err.println("提取图片失败: " + e.getMessage());
                }
            }

            System.out.println("成功提取 " + images.size() + " 张图片");

        } catch (Exception e) {
            System.err.println("提取图片过程中发生错误: " + e.getMessage());
        }

        return images;
    }

    /**
     * 解析文档内容，包括文本和图片
     */
    private static List<PageContent> parseDocumentContent(XWPFDocument document, Map<String, BufferedImage> images) {
        List<PageContent> pages = new ArrayList<>();
        PageContent currentPage = new PageContent();
        int imageIndex = 0;

        // 处理段落
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText();

            // 检查段落中是否包含图片
            boolean hasImages = false;
            for (XWPFRun run : paragraph.getRuns()) {
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                for (XWPFPicture picture : pictures) {
                    String imageKey = "img_" + imageIndex++;
                    if (imageIndex - 1 < images.size()) {
                        // 使用实际的图片
                        BufferedImage img = images.values().toArray(new BufferedImage[0])[imageIndex - 1];
                        PageImage pageImage = new PageImage(img, imageKey);

                        // 如果当前页空间不足，创建新页
                        if (currentPage.getCurrentHeight() + img.getHeight() > PAGE_HEIGHT - MARGIN * 2) {
                            pages.add(currentPage);
                            currentPage = new PageContent();
                        }

                        currentPage.addImage(pageImage);
                        hasImages = true;
                    }
                }
            }

            // 添加文本内容
            if (text != null && !text.trim().isEmpty()) {
                PageText pageText = new PageText(text, paragraph.getStyle());

                // 如果当前页空间不足，创建新页
                if (currentPage.getCurrentHeight() + estimateTextHeight(text) > PAGE_HEIGHT - MARGIN * 2) {
                    pages.add(currentPage);
                    currentPage = new PageContent();
                }

                currentPage.addText(pageText);
            }

            // 添加空行
            PageText emptyLine = new PageText("", "");
            currentPage.addText(emptyLine);
        }

        // 处理表格
        for (XWPFTable table : document.getTables()) {
            PageTable pageTable = parseTable(table);

            // 如果当前页空间不足，创建新页
            if (currentPage.getCurrentHeight() + estimateTableHeight(pageTable) > PAGE_HEIGHT - MARGIN * 2) {
                pages.add(currentPage);
                currentPage = new PageContent();
            }

            currentPage.addTable(pageTable);
        }

        // 添加最后一页
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        return pages;
    }

    /**
     * 解析表格内容
     */
    private static PageTable parseTable(XWPFTable table) {
        PageTable pageTable = new PageTable();

        for (XWPFTableRow row : table.getRows()) {
            List<String> rowData = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                rowData.add(cell.getText());
            }
            pageTable.addRow(rowData);
        }

        return pageTable;
    }

    /**
     * 渲染页面为图片
     */
    private static String renderPageToImage(PageContent page, String outputDir, String fileName) {
        try {
            BufferedImage image = new BufferedImage(PAGE_WIDTH, PAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // 设置高质量渲染
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            // 白色背景
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, PAGE_WIDTH, PAGE_HEIGHT);

            // 设置默认字体
            g2d.setColor(Color.BLACK);
            Font defaultFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
            g2d.setFont(defaultFont);

            // 渲染内容
            int currentY = MARGIN;

            // 渲染文本
            for (PageText text : page.getTexts()) {
                if (text.getText().isEmpty()) {
                    currentY += 10; // 空行
                    continue;
                }

                List<String> lines = wrapText(text.getText(), PAGE_WIDTH - MARGIN * 2, g2d);
                for (String line : lines) {
                    if (currentY < PAGE_HEIGHT - MARGIN) {
                        g2d.drawString(line, MARGIN, currentY);
                        currentY += 20;
                    }
                }
                currentY += 5; // 段落间距
            }

            // 渲染图片
            for (PageImage pageImage : page.getImages()) {
                BufferedImage img = pageImage.getImage();
                if (img != null) {
                    // 调整图片大小以适应页面宽度
                    int imgWidth = img.getWidth();
                    int imgHeight = img.getHeight();

                    if (imgWidth > PAGE_WIDTH - MARGIN * 2) {
                        double scale = (double) (PAGE_WIDTH - MARGIN * 2) / imgWidth;
                        imgWidth = (int) (imgWidth * scale);
                        imgHeight = (int) (imgHeight * scale);
                    }

                    // 检查是否有足够空间
                    if (currentY + imgHeight > PAGE_HEIGHT - MARGIN) {
                        // 图片超出当前页，需要特殊处理（这里简单跳过）
                        System.out.println("警告：图片超出页面范围，已跳过");
                        continue;
                    }

                    // 居中显示图片
                    int x = (PAGE_WIDTH - imgWidth) / 2;
                    g2d.drawImage(img, x, currentY, imgWidth, imgHeight, null);
                    currentY += imgHeight + 10;
                }
            }

            // 渲染表格
            for (PageTable table : page.getTables()) {
                currentY = renderTable(g2d, table, currentY, PAGE_WIDTH);
            }

            g2d.dispose();

            // 保存图片
            File imageFile = new File(outputDir, fileName);
            ImageIO.write(image, fileName.substring(fileName.lastIndexOf('.') + 1), imageFile);

            return imageFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("渲染页面失败: " + e.getMessage(), e);
        }
    }

    /**
     * 渲染表格
     */
    private static int renderTable(Graphics2D g2d, PageTable table, int startY, int pageWidth) {
        int currentY = startY;
        int rowHeight = 30;
        int colWidth = (pageWidth - MARGIN * 2) / Math.max(1, table.getMaxColumns());

        // 绘制表格边框
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(1));

        int tableWidth = colWidth * table.getMaxColumns();
        int tableHeight = rowHeight * table.getRowCount();
        int tableX = (pageWidth - tableWidth) / 2;

        // 绘制行和列
        for (int i = 0; i <= table.getRowCount(); i++) {
            g2d.drawLine(tableX, currentY + i * rowHeight,
                         tableX + tableWidth, currentY + i * rowHeight);
        }

        for (int i = 0; i <= table.getMaxColumns(); i++) {
            g2d.drawLine(tableX + i * colWidth, currentY,
                         tableX + i * colWidth, currentY + tableHeight);
        }

        // 绘制表格内容
        g2d.setColor(Color.BLACK);
        for (int i = 0; i < table.getRowCount(); i++) {
            List<String> row = table.getRows().get(i);
            for (int j = 0; j < row.size(); j++) {
                String cellText = row.get(j);
                if (cellText != null) {
                    // 文本换行
                    List<String> lines = wrapText(cellText, colWidth - 10, g2d);
                    for (int k = 0; k < lines.size() && k < 2; k++) { // 最多显示2行
                        int textY = currentY + (i * rowHeight) + 15 + (k * 15);
                        g2d.drawString(lines.get(k), tableX + (j * colWidth) + 5, textY);
                    }
                }
            }
        }

        return currentY + tableHeight + 10;
    }

    /**
     * 文本自动换行
     */
    private static List<String> wrapText(String text, int maxWidth, Graphics2D g2d) {
        List<String> lines = new ArrayList<>();
        FontMetrics fm = g2d.getFontMetrics();

        if (fm.stringWidth(text) <= maxWidth) {
            lines.add(text);
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine + (currentLine.length() > 0 ? " " : "") + word;
            if (fm.stringWidth(testLine) <= maxWidth) {
                currentLine.append(currentLine.length() > 0 ? " " : "").append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    /**
     * 估算文本高度
     */
    private static int estimateTextHeight(String text) {
        // 简单估算：每50个字符一行，每行20像素
        return (int) Math.ceil(text.length() / 50.0) * 20 + 10;
    }

    /**
     * 估算表格高度
     */
    private static int estimateTableHeight(PageTable table) {
        return table.getRowCount() * 30 + 20;
    }

    // ==================== LibreOffice相关方法 ====================

    public static boolean isLibreOfficeAvailable() {
        try {
            Process process;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                process = Runtime.getRuntime().exec("soffice --version");
            } else {
                process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "which soffice"});
            }
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static File convertWordToPdfWithLibreOffice(String wordFilePath, String outputDir) throws Exception {
        File wordFile = new File(wordFilePath);
        String pdfFileName = wordFile.getName().replace(".docx", ".pdf");
        File pdfFile = new File(outputDir, pdfFileName);

        String command;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            command = String.format("soffice --headless --convert-to pdf --outdir \"%s\" \"%s\"",
                                  outputDir, wordFile.getAbsolutePath());
        } else {
            command = String.format("libreoffice --headless --convert-to pdf --outdir \"%s\" \"%s\"",
                                  outputDir, wordFile.getAbsolutePath());
        }

        Process process = Runtime.getRuntime().exec(command);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("LibreOffice转换失败，退出码: " + exitCode);
        }

        Thread.sleep(2000);

        if (!pdfFile.exists()) {
            File[] pdfFiles = new File(outputDir).listFiles((dir, name) ->
                name.toLowerCase().endsWith(".pdf") &&
                name.toLowerCase().contains(wordFile.getName().replace(".docx", "").toLowerCase())
            );

            if (pdfFiles != null && pdfFiles.length > 0) {
                pdfFile = pdfFiles[0];
            } else {
                throw new FileNotFoundException("生成的PDF文件未找到");
            }
        }

        return pdfFile;
    }

    private static List<String> convertPdfToImages(File pdfFile, String outputDir, String format) {
        List<String> imagePaths = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            int pageCount = document.getNumberOfPages();
            System.out.println("转换PDF，共 " + pageCount + " 页");

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                try {
                    BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, DPI);

                    String imageName = String.format("page_%03d.%s", pageIndex + 1, format);
                    File imageFile = new File(outputDir, imageName);

                    ImageIO.write(image, format, imageFile);
                    imagePaths.add(imageFile.getAbsolutePath());

                    System.out.println("已转换第 " + (pageIndex + 1) + " 页");

                } catch (Exception e) {
                    System.err.println("转换第 " + (pageIndex + 1) + " 页失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("PDF转图片失败: " + e.getMessage(), e);
        }

        return imagePaths;
    }
}

// ==================== 数据模型类 ====================

/**
 * 页面内容容器
 */
class PageContent {
    private List<PageText> texts = new ArrayList<>();
    private List<PageImage> images = new ArrayList<>();
    private List<PageTable> tables = new ArrayList<>();
    private int currentHeight = 0;

    public void addText(PageText text) {
        texts.add(text);
        currentHeight += 20; // 估算高度
    }

    public void addImage(PageImage image) {
        images.add(image);
        currentHeight += image.getImage().getHeight() + 10;
    }

    public void addTable(PageTable table) {
        tables.add(table);
        currentHeight += table.getRowCount() * 30 + 20;
    }

    public boolean isEmpty() {
        return texts.isEmpty() && images.isEmpty() && tables.isEmpty();
    }

    public int getCurrentHeight() {
        return currentHeight;
    }

    // Getters
    public List<PageText> getTexts() { return texts; }
    public List<PageImage> getImages() { return images; }
    public List<PageTable> getTables() { return tables; }
}

/**
 * 页面文本
 */
class PageText {
    private String text;
    private String style;

    public PageText(String text, String style) {
        this.text = text;
        this.style = style;
    }

    // Getters
    public String getText() { return text; }
    public String getStyle() { return style; }
}

/**
 * 页面图片
 */
class PageImage {
    private BufferedImage image;
    private String imageId;

    public PageImage(BufferedImage image, String imageId) {
        this.image = image;
        this.imageId = imageId;
    }

    // Getters
    public BufferedImage getImage() { return image; }
    public String getImageId() { return imageId; }
}

/**
 * 页面表格
 */
class PageTable {
    private List<List<String>> rows = new ArrayList<>();

    public void addRow(List<String> row) {
        rows.add(row);
    }

    public int getRowCount() {
        return rows.size();
    }

    public int getMaxColumns() {
        return rows.stream().mapToInt(List::size).max().orElse(0);
    }

    // Getters
    public List<List<String>> getRows() { return rows; }
}