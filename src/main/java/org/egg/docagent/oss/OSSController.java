package org.egg.docagent.oss;

import org.egg.docagent.ossutil.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileInputStream;
import java.util.List;

@RestController
public class OSSController {
    @Autowired
    private OSSUtil ossUtil;

    @PostMapping(value = "/oss-upload")
    public String upload(@RequestParam("path") String path, @RequestParam("uploadPath") String uploadPath) throws Exception{
        try {
            ossUtil.upload(new FileInputStream(path), uploadPath);
            return "success";
        } catch (Exception e) {
            return "fail";
        }
    }

    @PostMapping(value = "/oss-url")
    public String url(String path) {
        String url = ossUtil.url(path);
        return url;
    }

    @PostMapping(value = "/oss-list-files")
    public List<String> listObjects() {
        return ossUtil.list();
    }

    @PostMapping(value = "/oss-delete")
    public String delete(@RequestParam("path") String path) {
        ossUtil.delete(path);
        return "success";
    }

}
