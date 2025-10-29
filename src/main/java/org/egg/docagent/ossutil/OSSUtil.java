package org.egg.docagent.ossutil;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;

@Configuration
public class OSSUtil implements InitializingBean {
    @Value("${oss-endpoint}")
    private String endpoint;
    @Value("${oss-region}")
    private String region;
    @Value("${oss-access-key}")
    private String ak;
    @Value("${oss-secret-key}")
    private String sk;
    @Value("${oss-bucket}")
    private String bucket;
    @Value("${oss-expire-time}")
    private long expireTime;

    private OSS ossClient;


    /**
     * 上传文件
     */
    public void upload(InputStream inputStream, String path) {
        try {
            PutObjectRequest request = new PutObjectRequest(bucket, path, inputStream);
            PutObjectResult result = ossClient.putObject(request);
            return;
        } catch (Exception e) {
            System.err.println("上传失败: " + path + "\n" + e);
        }
        throw new RuntimeException("上传失败: " + path);
    }

    /**
     * 获取文件的下载URL
     */
    public String url(String path) {
        try {
            URL url = ossClient.generatePresignedUrl(bucket, path, new Date(new Date().getTime() + expireTime));
            return url.toString();

        } catch (Exception e) {
            System.err.println("获取URL失败: " + path + "\n" + e);

        }
        throw new RuntimeException("获取URL失败: " + path + "\n");
    }

    /**
     * 罗列bucket下的文件
     */
    public List<String> list() {
        ObjectListing objectListing = ossClient.listObjects(bucket);
        List<OSSObjectSummary> list = objectListing.getObjectSummaries();
        return list.stream().map(OSSObjectSummary::getKey).toList();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ClientBuilderConfiguration configuration;
        configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        ossClient = OSSClientBuilder.create()
                .endpoint(endpoint)
                .region(region)
                .credentialsProvider(new DefaultCredentialProvider(ak, sk))
                .clientConfiguration(configuration)
                .build();
    }
}
