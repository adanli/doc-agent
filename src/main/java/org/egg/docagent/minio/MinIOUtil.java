package org.egg.docagent.minio;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MinIOUtil implements InitializingBean {
    @Value("${minio-endpoint}")
    private String endpoint;
    @Value("${minio-access-key}")
    private String accessKey;
    @Value("${minio-secret-key}")
    private String secretKey;
    @Value("${minio-bucket}")
    private String bucket;
    @Value("${minio-expire-time}")
    private int expireTime;


    private MinioClient client;

    @Override
    public void afterPropertiesSet() throws Exception {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 上传
     */
    public void upload(String filePath, String uploadPath) {
        try {
            client.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .filename(filePath)
                            .object(uploadPath)
                            .build()
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取URL
     */
    public String url(String path) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .expiry(expireTime)
                            .method(Method.GET)
                    .build());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 删除
     */
    public void delete(String path) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                    .build());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 列出bucket下的objects
     */
    public List<String> list() {
        try {
            List<Bucket> list = client.listBuckets();
            return list.stream().map(Bucket::name).toList();

        } catch (Exception e) {
            System.err.println(e);
        }
        return new ArrayList<>();
    }

}
