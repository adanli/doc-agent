package org.egg.docagent.entity;

import lombok.Data;


@Data
public class FileContent {
    private String id;
    private String fileName;
    private Long createDt;
    private Long updateDt;
    private String filePath;
    private float[] fileContent;
    private String  sourceContent;
    private Boolean existContent;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getCreateDt() {
        return createDt;
    }

    public void setCreateDt(Long createDt) {
        this.createDt = createDt;
    }

    public Long getUpdateDt() {
        return updateDt;
    }

    public void setUpdateDt(Long updateDt) {
        this.updateDt = updateDt;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public float[] getFileContent() {
        return fileContent;
    }

    public void setFileContent(float[] fileContent) {
        this.fileContent = fileContent;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    public void setSourceContent(String sourceContent) {
        this.sourceContent = sourceContent;
    }

    public Boolean getExistContent() {
        return existContent;
    }

    public void setExistContent(Boolean existContent) {
        this.existContent = existContent;
    }
}
