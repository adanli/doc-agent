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
}
