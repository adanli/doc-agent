package org.egg.docagent.minio;

import io.minio.MinioClient;
import io.minio.messages.Bucket;

import java.util.List;

public class MinIOExample {
    private static MinioClient client;

    static {
        client = MinioClient.builder()
//                .endpoint("http://10.0.0.199:9000")
                .endpoint("http://www.triplesails.com:43390")
//                .endpoint("http://101.64.134.253:43390")
                .credentials("walxMVvEmIMgCcwEjN5g", "YHq9CZsj15WidWpwdEFQMSUHU5RrsswNitjGvUMA")
                .build();
    }

    public static void main(String[] args) {
        try {
            List<Bucket> list = client.listBuckets();
            list.forEach(b -> System.out.println(b.name()));

        } catch (Exception e) {
            System.err.println(e);
        }
    }
}
