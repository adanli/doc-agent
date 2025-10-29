package org.egg.docagent.excel2image;


import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.util.IOUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelToImageConverter {

    // 固定输出尺寸
    private static final int FIXED_WIDTH = 3840;
    private static final int FIXED_HEIGHT = 2160;

    // 表格配置
    private static final int ROW_HEIGHT = 360;
    private static final int COLUMN_WIDTH = 120;
    private static final int HEADER_HEIGHT = 35;
    private static final int MARGIN = 25;
    private static final Font HEADER_FONT = new Font("Microsoft YaHei", Font.BOLD, 14);
    private static final Font DATA_FONT = new Font("Microsoft YaHei", Font.PLAIN, 12);
    private static final Font TITLE_FONT = new Font("Microsoft YaHei", Font.BOLD, 18);
    private static final Font PAGE_FONT = new Font("Microsoft YaHei", Font.PLAIN, 12);

    // 颜色配置
    private static final Color HEADER_BG_COLOR = new Color(70, 130, 180);
    private static final Color HEADER_BORDER_COLOR = new Color(40, 90, 140);
    private static final Color HEADER_TEXT_COLOR = Color.WHITE;
    private static final Color EVEN_ROW_COLOR = new Color(255, 255, 255);
    private static final Color ODD_ROW_COLOR = new Color(248, 250, 252);
    private static final Color GRID_COLOR = new Color(220, 220, 220);
    private static final Color TITLE_COLOR = new Color(50, 50, 80);
    private static final Color BACKGROUND_COLOR = new Color(245, 247, 250);
    private static final Color PAGE_INFO_COLOR = new Color(100, 100, 100);

    static {
        // 设置POI内存限制
        IOUtils.setByteArrayMaxOverride(256 * 1024 * 1024);
    }

    /**
     * 将Excel文件转换为固定尺寸的图片（自动分页）
     */
    public static List<String> convertToFixedSizeImages(String excelFilePath, String outputDir, String format) {
        List<String> imagePaths = new ArrayList<>();

        try {
            File excelFile = new File(excelFilePath);
            if (!excelFile.exists()) {
                throw new FileNotFoundException("Excel文件不存在: " + excelFilePath);
            }

            // 创建输出目录
            File outputDirectory = new File(outputDir);
            if (!outputDirectory.exists()) {
                outputDirectory.mkdirs();
            }

            System.out.println("开始处理Excel文件: " + excelFile.getName());

            // 加载Excel工作簿
            Workbook workbook = loadWorkbook(excelFile);
            int sheetCount = workbook.getNumberOfSheets();

            System.out.println("找到 " + sheetCount + " 个工作表");

            // 处理每个工作表
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();

                if (isSheetEmpty(sheet)) {
                    System.out.println("跳过空工作表: " + sheetName);
                    continue;
                }

                System.out.println("正在转换工作表: " + sheetName);

                // 转换工作表（可能生成多个图片）
                List<String> sheetImages = convertSheetToFixedSizeImages(sheet, outputDir, format, i + 1);
                imagePaths.addAll(sheetImages);

                System.out.println("✓ 工作表 '" + sheetName + "' 生成 " + sheetImages.size() + " 张图片");
            }

            workbook.close();
            System.out.println("所有工作表转换完成！共生成 " + imagePaths.size() + " 张图片");

        } catch (Exception e) {
            throw new RuntimeException("Excel转换失败: " + e.getMessage(), e);
        }

        return imagePaths;
    }

    /**
     * 转换单个工作表为固定尺寸图片（自动分页）
     */
    private static List<String> convertSheetToFixedSizeImages(Sheet sheet, String outputDir,
                                                            String format, int sheetNumber) {
        List<String> imagePaths = new ArrayList<>();

        try {
            String sheetName = sheet.getSheetName();

            // 计算可用内容区域
            int contentWidth = FIXED_WIDTH - MARGIN * 2;
            int contentHeight = FIXED_HEIGHT - MARGIN * 2 - 60; // 减去标题和页码区域

            // 计算每页能显示的行数和列数
            int maxColumnsPerPage = contentWidth / COLUMN_WIDTH;
            int maxRowsPerPage = contentHeight / ROW_HEIGHT;

            // 计算总列数和总行数
            SheetInfo sheetInfo = analyzeSheet(sheet);
            int totalColumns = sheetInfo.getMaxColumns();
            int totalRows = sheetInfo.getRowCount();

            System.out.printf("工作表信息: %d 列 × %d 行, 每页显示: %d 列 × %d 行%n",
                totalColumns, totalRows, maxColumnsPerPage, maxRowsPerPage);

            // 计算需要的页数（列方向 × 行方向）
            int columnPages = (int) Math.ceil((double) totalColumns / maxColumnsPerPage);
            int rowPages = (int) Math.ceil((double) totalRows / maxRowsPerPage);
            int totalPages = columnPages * rowPages;

            System.out.printf("需要 %d × %d = %d 页%n", columnPages, rowPages, totalPages);

            // 生成每一页
            for (int colPage = 0; colPage < columnPages; colPage++) {
                for (int rowPage = 0; rowPage < rowPages; rowPage++) {
                    int pageNumber = colPage * rowPages + rowPage + 1;

                    // 计算当前页的数据范围
                    int startCol = colPage * maxColumnsPerPage;
                    int endCol = Math.min(startCol + maxColumnsPerPage - 1, totalColumns - 1);
                    int startRow = rowPage * maxRowsPerPage;
                    int endRow = Math.min(startRow + maxRowsPerPage - 1, totalRows - 1);

                    String imagePath = createPageImage(sheet, sheetName, outputDir, format,
                                                     sheetNumber, pageNumber, totalPages,
                                                     startCol, endCol, startRow, endRow,
                                                     maxColumnsPerPage, maxRowsPerPage);

                    if (imagePath != null) {
                        imagePaths.add(imagePath);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("转换工作表失败: " + e.getMessage());
            e.printStackTrace();
        }

        return imagePaths;
    }

    /**
     * 创建单页图片
     */
    private static String createPageImage(Sheet sheet, String sheetName, String outputDir,
                                        String format, int sheetNumber, int pageNumber,
                                        int totalPages, int startCol, int endCol,
                                        int startRow, int endRow, int maxColumnsPerPage,
                                        int maxRowsPerPage) {
        try {
            // 创建固定尺寸的图片
            BufferedImage image = new BufferedImage(FIXED_WIDTH, FIXED_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // 设置高质量渲染
            setupHighQualityRendering(g2d);

            // 绘制背景
            drawBackground(g2d, FIXED_WIDTH, FIXED_HEIGHT);

            // 绘制标题和页码信息
            String title = String.format("%s (第%d页/共%d页)", sheetName, pageNumber, totalPages);
            drawSheetTitle(g2d, title, FIXED_WIDTH);

            // 绘制表格内容
            drawPageContent(g2d, sheet, startCol, endCol, startRow, endRow,
                          maxColumnsPerPage, maxRowsPerPage);

            // 绘制页面信息
            drawPageInfo(g2d, startCol, endCol, startRow, endRow, FIXED_WIDTH, FIXED_HEIGHT);

            g2d.dispose();

            // 保存图片
            return savePageImage(image, sheetName, outputDir, format, sheetNumber, pageNumber);

        } catch (Exception e) {
            System.err.println("创建页面图片失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 分析工作表信息
     */
    private static SheetInfo analyzeSheet(Sheet sheet) {
        int maxColumns = 0;
        int rowCount = 0;

        for (int rowNum = sheet.getFirstRowNum(); rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row != null) {
                maxColumns = Math.max(maxColumns, row.getLastCellNum());
                rowCount++;
            }
        }

        maxColumns = Math.max(maxColumns, 1);
        rowCount = Math.max(rowCount, 1);

        return new SheetInfo(maxColumns, rowCount);
    }

    /**
     * 绘制页面内容
     */
    private static void drawPageContent(Graphics2D g2d, Sheet sheet, int startCol, int endCol,
                                      int startRow, int endRow, int maxColumnsPerPage,
                                      int maxRowsPerPage) {
        int tableStartY = MARGIN + 60; // 标题区域下方

        // 绘制表头
        drawPageHeader(g2d, startCol, endCol, tableStartY);

        // 绘制数据行
        drawPageDataRows(g2d, sheet, startCol, endCol, startRow, endRow, tableStartY + HEADER_HEIGHT);

        // 绘制网格线
        drawPageGridLines(g2d, startCol, endCol, startRow, endRow, tableStartY, maxRowsPerPage);
    }

    /**
     * 绘制页面表头
     */
    private static void drawPageHeader(Graphics2D g2d, int startCol, int endCol, int startY) {
        int currentX = MARGIN;
        int columnsInPage = endCol - startCol + 1;

        // 表头背景
        g2d.setColor(HEADER_BG_COLOR);
        g2d.fillRect(MARGIN, startY, columnsInPage * COLUMN_WIDTH, HEADER_HEIGHT);

        // 表头边框
        g2d.setColor(HEADER_BORDER_COLOR);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRect(MARGIN, startY, columnsInPage * COLUMN_WIDTH, HEADER_HEIGHT);

        // 表头文字
        g2d.setColor(HEADER_TEXT_COLOR);
        g2d.setFont(HEADER_FONT);

        for (int col = startCol; col <= endCol; col++) {
            String headerText = getColumnName(col);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(headerText);
            int textX = currentX + (COLUMN_WIDTH - textWidth) / 2;
            int textY = startY + HEADER_HEIGHT / 2 + fm.getHeight() / 4;

            g2d.drawString(headerText, textX, textY);
            currentX += COLUMN_WIDTH;
        }
    }

    /**
     * 绘制页面数据行
     */
    private static void drawPageDataRows(Graphics2D g2d, Sheet sheet, int startCol, int endCol,
                                       int startRow, int endRow, int startY) {
        int currentY = startY;
        int displayRowIndex = 0;

        for (int rowNum = startRow; rowNum <= endRow; rowNum++) {
            Row row = sheet.getRow(getActualRowIndex(sheet, rowNum));
            if (row != null) {
                drawPageDataRow(g2d, row, startCol, endCol, currentY, displayRowIndex);
                currentY += ROW_HEIGHT;
                displayRowIndex++;
            }
        }
    }

    /**
     * 获取实际的行索引
     */
    private static int getActualRowIndex(Sheet sheet, int displayRowIndex) {
        int actualIndex = sheet.getFirstRowNum();
        int count = 0;

        for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                if (count == displayRowIndex) {
                    return i;
                }
                count++;
            }
        }

        return actualIndex;
    }

    /**
     * 绘制页面单行数据
     */
    private static void drawPageDataRow(Graphics2D g2d, Row row, int startCol, int endCol,
                                      int y, int rowIndex) {
        int currentX = MARGIN;

        // 行背景（交替颜色）
        Color rowColor = (rowIndex % 2 == 0) ? EVEN_ROW_COLOR : ODD_ROW_COLOR;
        g2d.setColor(rowColor);
        g2d.fillRect(MARGIN, y, (endCol - startCol + 1) * COLUMN_WIDTH, ROW_HEIGHT);

        // 单元格内容
        g2d.setColor(Color.BLACK);
        g2d.setFont(DATA_FONT);

        for (int col = startCol; col <= endCol; col++) {
            Cell cell = row.getCell(col, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            String cellValue = getCellValue(cell);

            // 文本位置和裁剪
            FontMetrics fm = g2d.getFontMetrics();
            String displayText = truncateTextToFit(cellValue, fm, COLUMN_WIDTH - 10);
            int textX = currentX + 8;
            int textY = y + ROW_HEIGHT / 2 + fm.getHeight() / 4;

            g2d.drawString(displayText, textX, textY);
            currentX += COLUMN_WIDTH;
        }

        // 行边框
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));
        g2d.drawRect(MARGIN, y, (endCol - startCol + 1) * COLUMN_WIDTH, ROW_HEIGHT);
    }

    /**
     * 绘制页面网格线
     */
    private static void drawPageGridLines(Graphics2D g2d, int startCol, int endCol,
                                        int startRow, int endRow, int startY, int maxRowsPerPage) {
        g2d.setColor(GRID_COLOR);
        g2d.setStroke(new BasicStroke(1));

        int columnsInPage = endCol - startCol + 1;
        int rowsInPage = endRow - startRow + 1;
        int totalWidth = columnsInPage * COLUMN_WIDTH;
        int totalHeight = (rowsInPage + 1) * ROW_HEIGHT;

        // 垂直线
        for (int col = 0; col <= columnsInPage; col++) {
            int x = MARGIN + col * COLUMN_WIDTH;
            g2d.drawLine(x, startY, x, startY + totalHeight);
        }

        // 水平线
        for (int row = 0; row <= rowsInPage + 1; row++) {
            int y = startY + row * ROW_HEIGHT;
            g2d.drawLine(MARGIN, y, MARGIN + totalWidth, y);
        }
    }

    /**
     * 绘制页面信息
     */
    private static void drawPageInfo(Graphics2D g2d, int startCol, int endCol,
                                   int startRow, int endRow, int width, int height) {
        g2d.setColor(PAGE_INFO_COLOR);
        g2d.setFont(PAGE_FONT);

        String columnRange = String.format("列: %s-%s",
            getColumnName(startCol), getColumnName(endCol));
        String rowRange = String.format("行: %d-%d", startRow + 1, endRow + 1);

        FontMetrics fm = g2d.getFontMetrics();
        int infoY = height - 15;

        // 列范围（左下角）
        g2d.drawString(columnRange, MARGIN, infoY);

        // 行范围（右下角）
        int rowRangeWidth = fm.stringWidth(rowRange);
        g2d.drawString(rowRange, width - MARGIN - rowRangeWidth, infoY);
    }

    /**
     * 保存页面图片
     */
    private static String savePageImage(BufferedImage image, String sheetName,
                                      String outputDir, String format,
                                      int sheetNumber, int pageNumber) {
        try {
            String cleanName = sheetName.replaceAll("[\\\\/:*?\"<>|]", "_");
            String imageName = String.format("%s_%d_%d.%s", cleanName, sheetNumber, pageNumber, format.toLowerCase());
            File imageFile = new File(outputDir, imageName);

            ImageIO.write(image, format, imageFile);
            return imageFile.getAbsolutePath();

        } catch (Exception e) {
            throw new RuntimeException("保存页面图片失败: " + e.getMessage(), e);
        }
    }

    // ========== 以下方法与之前相同，为保持完整性保留 ==========

    private static Workbook loadWorkbook(File excelFile) throws IOException {
        String fileName = excelFile.getName().toLowerCase();
        FileInputStream fis = new FileInputStream(excelFile);

        if (fileName.endsWith(".xlsx")) {
            return new XSSFWorkbook(fis);
        } else if (fileName.endsWith(".xls")) {
            return new HSSFWorkbook(fis);
        } else {
            fis.close();
            throw new IllegalArgumentException("不支持的文件格式");
        }
    }

    private static boolean isSheetEmpty(Sheet sheet) {
        return sheet.getPhysicalNumberOfRows() == 0;
    }

    private static void setupHighQualityRendering(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void drawBackground(Graphics2D g2d, int width, int height) {
        GradientPaint gradient = new GradientPaint(
            0, 0, new Color(250, 252, 255),
            width, height, new Color(240, 245, 250)
        );
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);
    }

    private static void drawSheetTitle(Graphics2D g2d, String title, int width) {
        g2d.setColor(TITLE_COLOR);
        g2d.setFont(TITLE_FONT);

        FontMetrics fm = g2d.getFontMetrics();
        int titleWidth = fm.stringWidth(title);
        int titleX = (width - titleWidth) / 2;
        int titleY = MARGIN + 30;

        g2d.drawString(title, titleX, titleY);

        // 标题下划线
        g2d.setColor(new Color(70, 130, 180));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(titleX - 20, titleY + 5, titleX + titleWidth + 20, titleY + 5);
    }

    private static String getColumnName(int columnIndex) {
        StringBuilder sb = new StringBuilder();
        while (columnIndex >= 0) {
            sb.append((char) ('A' + columnIndex % 26));
            columnIndex = columnIndex / 26 - 1;
        }
        return sb.reverse().toString();
    }

    private static String getCellValue(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        return cell.getDateCellValue().toString();
                    } else {
                        double value = cell.getNumericCellValue();
                        if (value == Math.floor(value)) {
                            return String.format("%d", (long) value);
                        } else {
                            return String.format("%.2f", value);
                        }
                    }
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                case FORMULA:
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e) {
                        try {
                            return cell.getStringCellValue();
                        } catch (Exception e2) {
                            return cell.getCellFormula();
                        }
                    }
                default:
                    return "";
            }
        } catch (Exception e) {
            return "#ERROR";
        }
    }

    private static String truncateTextToFit(String text, FontMetrics fm, int maxWidth) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }

        int low = 0;
        int high = text.length();
        String ellipsis = "...";

        while (low < high) {
            int mid = (low + high) / 2;
            String test = text.substring(0, mid) + ellipsis;

            if (fm.stringWidth(test) < maxWidth) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        int cutPoint = Math.max(0, low - 1);
        if (cutPoint == 0) {
            return ellipsis;
        }

        return text.substring(0, cutPoint) + ellipsis;
    }
}

/**
 * 工作表信息类
 */
class SheetInfo {
    private int maxColumns;
    private int rowCount;

    public SheetInfo(int maxColumns, int rowCount) {
        this.maxColumns = maxColumns;
        this.rowCount = rowCount;
    }

    public int getMaxColumns() { return maxColumns; }
    public int getRowCount() { return rowCount; }
}