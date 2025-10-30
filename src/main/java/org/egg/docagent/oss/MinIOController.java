package org.egg.docagent.oss;

import org.egg.docagent.minio.MinIOUtil;
import org.egg.docagent.ossutil.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileInputStream;
import java.util.List;

@RestController
public class MinIOController {
    @Autowired
    private MinIOUtil minIOUtil;

    @PostMapping(value = "/minio-upload")
    public String upload(@RequestParam("path") String path, @RequestParam("uploadPath") String uploadPath) throws Exception{
        try {
            minIOUtil.upload(path, uploadPath);
            return "success";
        } catch (Exception e) {
            return "fail";
        }
    }

    @PostMapping(value = "/minio-url")
    public String url(@RequestParam("path") String path) {
        String url = minIOUtil.url(path);
        return url;
    }

    @PostMapping(value = "/minio-list-files")
    public List<String> listObjects() {
        return minIOUtil.list();
    }

    @PostMapping(value = "/minio-delete")
    public String delete(@RequestParam("path") String path) {
        minIOUtil.delete(path);
        return "success";
    }

}
