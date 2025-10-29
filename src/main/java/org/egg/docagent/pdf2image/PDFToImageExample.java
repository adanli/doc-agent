package org.egg.docagent.pdf2image;

import java.util.List;

public class PDFToImageExample {

    public static void main(String[] args) {
        String path = "D:\\doc\\cic\\履约\\赔付率归因\\5. 行业参考\\保险赔付率归因分析方法研究与应用 .pdf";

        List<String> list = PDFToImageConverter.convertToImages(path, "D:\\doc_out");
        list.forEach(System.out::println);

    }
}