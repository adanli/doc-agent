package org.egg.docagent.entity;

import lombok.Data;

import java.util.Arrays;
import java.util.Objects;


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
    private int version = 1;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

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

    @Override
    public String toString() {
        return "FileContent{" +
                "id='" + id + '\'' +
                ", fileName='" + fileName + '\'' +
                ", createDt=" + createDt +
                ", updateDt=" + updateDt +
                ", filePath='" + filePath + '\'' +
                ", fileContent=" + Arrays.toString(fileContent) +
                ", sourceContent='" + sourceContent + '\'' +
                ", existContent=" + existContent +
                '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        FileContent that = (FileContent) object;
        return Objects.equals(id, that.id) && Objects.equals(fileName, that.fileName) && Objects.equals(createDt, that.createDt) && Objects.equals(updateDt, that.updateDt) && Objects.equals(filePath, that.filePath) && Objects.deepEquals(fileContent, that.fileContent) && Objects.equals(sourceContent, that.sourceContent) && Objects.equals(existContent, that.existContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fileName, createDt, updateDt, filePath, Arrays.hashCode(fileContent), sourceContent, existContent);
    }
}
