package org.egg.docagent.excel2image;

import java.util.List;

public class ExcelToImageExample {
    public static void main(String[] args) {
        String input = "D:\\doc\\cic\\准备金\\20241220案件维度测算-新增伤情不确认案均逻辑与新疆地方兵团处理.xlsx";
        String output = "D:\\doc_out";
        List<String> list = ExcelToImageConverter.convertToFixedSizeImages(input, output, "png");
        list.forEach(System.out::println);
    }
}
