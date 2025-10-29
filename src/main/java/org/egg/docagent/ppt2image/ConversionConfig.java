package org.egg.docagent.ppt2image;

public class ConversionConfig {
    private String outputFormat = "png";
    private double scale = 2.0;
    private boolean createSubdirectory = true;
    private String imageQuality = "high";

    // 构造函数
    public ConversionConfig() {}

    public ConversionConfig(String outputFormat, double scale) {
        this.outputFormat = outputFormat;
        this.scale = scale;
    }

    // Getter和Setter
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) {
        if (!outputFormat.matches("(?i)png|jpg|jpeg|bmp|gif")) {
            throw new IllegalArgumentException("不支持的图片格式: " + outputFormat);
        }
        this.outputFormat = outputFormat;
    }

    public double getScale() { return scale; }
    public void setScale(double scale) {
        if (scale <= 0 || scale > 10) {
            throw new IllegalArgumentException("缩放比例必须在0-10之间");
        }
        this.scale = scale;
    }

    public boolean isCreateSubdirectory() { return createSubdirectory; }
    public void setCreateSubdirectory(boolean createSubdirectory) {
        this.createSubdirectory = createSubdirectory;
    }

    public String getImageQuality() { return imageQuality; }
    public void setImageQuality(String imageQuality) {
        this.imageQuality = imageQuality;
    }
}
