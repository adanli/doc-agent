package org.egg.docagent.chat;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.*;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.http.Method;
import org.egg.docagent.entity.FileContent;
import org.egg.docagent.pdf2image.PDFToImageConverter;
import org.egg.docagent.ppt2image.PPTToImageConverter;
import org.egg.docagent.util.ImageCompressor;
import org.egg.docagent.word2image.WordToImageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.File;
import java.io.IOException;
import java.lang.Thread;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatExecute implements Runnable{
    private static final String DATABASE = "default";
    private static final String COLLECTION = "vector_store";

    private List<String> files;
    private AtomicInteger successCount;
    private AtomicInteger errorCount;
    private AtomicInteger skipCount;

    private String prompt;
    private OpenAIClient openAIClient;
    private String outPath;
    private MinioClient minioClient;

    private String sk;
    private String picModel;
    private String model;

    private MilvusServiceClient milvusServiceClient;
    private CountDownLatch latch;
    private String baseUrl;

    private String minioEndpoint;
    private String minioAccessKey;
    private String minioSecretKey;
    private String bucket;

    public ChatExecute(List<String> files, AtomicInteger successCount, AtomicInteger errorCount, AtomicInteger skipCount, String prompt, String outPath, String sk, String picModel, String model, CountDownLatch latch,
        String baseUrl,
        String minioEndpoint,
        String minioAccessKey,
        String minioSecretKey,
        String bucket,
        MilvusServiceClient milvusServiceClient

    ) {
        this.files = files;
        this.successCount = successCount;
        this.errorCount = errorCount;
        this.skipCount = skipCount;
        this.prompt = prompt;
        this.outPath = outPath;
        this.sk = sk;
        this.picModel = picModel;
        this.model = model;
        this.latch = latch;
        this.baseUrl = baseUrl;
        this.minioEndpoint = minioEndpoint;
        this.minioAccessKey = minioAccessKey;
        this.minioSecretKey = minioSecretKey;
        this.bucket = bucket;
        this.milvusServiceClient = milvusServiceClient;

        init();

    }

    private void init() {
         openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(sk)
                .baseUrl(String.format("%s/%s", baseUrl, "v1"))
                .build();

         minioClient = MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(minioAccessKey, minioSecretKey)
                .build();

    }

    /**
     * 上传
     */
    public void upload(String filePath, String uploadPath) {

//        String format = ImageCompressor.getImageFormat(filePath);

        try {
//            byte[] imageData = ImageCompressor.compressWithThumbnailator(new FileInputStream(filePath), format, 800, 800, 0.8);
//            ByteArrayInputStream compressedStream = new ByteArrayInputStream(imageData);

            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .filename(filePath)
                            .object(uploadPath)
                            .build()
            );

            /*client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(uploadPath)
                .stream(compressedStream, imageData.length, -1)
                .contentType("image/png")
                .build());*/

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取URL
     */
    public String url(String path) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .expiry(300)
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
            minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                    .build());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        for (String file: files) {
            long s1 = System.currentTimeMillis();
            try {
                // 如果该文件存在，则跳过不处理
                file = file.replaceAll("\\\\", "/");

                FileContent content = this.findById(file);
                if(content != null) {
                    skipCount.incrementAndGet();
                    continue;
                }

                summaryFileContent(file);
                successCount.incrementAndGet();
            } catch (Exception e) {
                e.printStackTrace();
                errorCount.incrementAndGet();
            }
            System.out.printf("%s---%s文件解析完成，耗时: %ss，成功进度: %d/%d, 失败进度: %d/%d, 跳过进度: %d/%d%n", Thread.currentThread().getName(), file, (System.currentTimeMillis()-s1)/1000, successCount.get(), files.size(), errorCount.get(), files.size(), skipCount.get(), files.size());
        }

        latch.countDown();
    }

     public FileContent findById(@RequestParam(value = "id") String id) {
        QueryParam param = QueryParam.newBuilder()
                .withDatabaseName(DATABASE)
                .withCollectionName(COLLECTION)
                .withExpr(String.format("id == \"%s\"", id))
                .withOutFields(List.of("id", "file_name", "create_dt", "update_dt", "file_path", "file_content", "source_content", "exist_content", "version"))
                .withLimit(5L)
                .build();
        R<QueryResults> r = milvusServiceClient.query(param);

        if(r.getData() == null) return null;

        QueryResultsWrapper wrapper = new QueryResultsWrapper(r.getData());
        List<QueryResultsWrapper.RowRecord> list = wrapper.getRowRecords();

        if(CollectionUtils.isEmpty(list)) return null;
        QueryResultsWrapper.RowRecord record = list.get(0);

        return this.convert(record);
    }

    private FileContent convert(QueryResultsWrapper.RowRecord record) {
        FileContent content = new FileContent();
        if(!ObjectUtils.isEmpty(record.get("id"))) {
            content.setId(record.get("id")+"");
        }
        if(!ObjectUtils.isEmpty(record.get("file_name"))) {
            content.setFileName(record.get("file_name")+"");
        }
        if(!ObjectUtils.isEmpty(record.get("create_dt"))) {
            content.setCreateDt(Long.parseLong(record.get("create_dt")+""));
        }
        if(!ObjectUtils.isEmpty(record.get("update_dt"))) {
            content.setUpdateDt(Long.parseLong(record.get("update_dt")+""));
        }
        if(!ObjectUtils.isEmpty(record.get("version"))) {
            content.setVersion(Integer.parseInt(record.get("version")+""));
        }
        return content;
    }

    private String summaryFileContent(@RequestParam("path") String path) throws Exception{
        if(path.endsWith(".pptx") || path.endsWith(".docx") || path.endsWith(".xlsx")) {
            this.summaryFileContentWithPic(path);
        }   else {
            this.summaryNormalFileContent(path);
        }
        return "success";
    }

    /**
     * 1. 将PPT转成图片
     * 2. 使用大模型，解析图片
     * 3. 将文件夹内图片，解析后合并到一个文件
     */
    private void summaryFileContentWithPic(String path) {
        String _p = path.replaceAll(":","_").replaceAll("\\\\","_").replaceAll("/","_");
        String format = "png";
        String outputDir = String.format("%s/%s_dir", outPath, _p);
        File file = new File(outputDir);
        if(!file.exists()) {
            file.mkdirs();
        }

        try {
            if(path.endsWith(".pptx")) {
                PPTToImageConverter.convertToImages(path, outputDir, format);
            } else if(path.endsWith(".docx")) {
                WordToImageConverter.convertToImages(path, outputDir, format);
            } else if(path.endsWith(".pdf")) {
                PDFToImageConverter.convertToImages(path, outputDir, format);
            }

            String[] files = file.list();
            FileContent fileContent = new FileContent();
            this.getBasicFileInfo(path, fileContent);

            String outputFile = String.format("%s/%s.txt", outPath, _p);
            try {
                File newFile = new File(outputFile);
                if(newFile.exists()) {
                    newFile.delete();
                }
                if(!newFile.exists()) {
                    newFile.createNewFile();
                }

            } catch (Exception e) {
                throw new RuntimeException("创建文件失败: " + path);
            }

            Path p = Paths.get(outputFile);
            try {

                Files.write(p, (fileContent.getFilePath()+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.WRITE);
                // 文件名称
                Files.write(p, (fileContent.getFileName()+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);

                // 创建时间
                Files.write(p, (fileContent.getCreateDt()+""+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
                // 修改时间
                Files.write(p, (fileContent.getUpdateDt()+""+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);

                if(StringUtils.hasLength(fileContent.getSourceContent())) {
                    Files.write(p, fileContent.getSourceContent().getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            for(String f: files) {
                // 区分线程
                String nf = String.format("%s/%s", Thread.currentThread().getName(), f);

                String pf = String.format("%s/%s", outputDir, f);
                // 上传到oss
                try {
                    this.upload(pf, nf);
                } catch (Exception e) {
                    throw new RuntimeException("上传到oss失败: " + nf);
                }

                String content = this.summaryPicture(nf);
//                sb.append(content);
                try {
                    Files.write(p, (content+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                this.delete(f);
            }
//            fileContent.setSourceContent(sb.toString());

        } finally {
            clearDir(file);
        }


    }

    /**
     * 识别图片
     */
    @PostMapping(value = "/summary-picture")
    public String summaryPicture(@RequestParam("path") String path) {
        String imagePath = this.url(path);
//        String imagePath = ossUtil.url(path);

        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", imagePath),
                        Collections.singletonMap("text", prompt))).build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(sk)
                .model(picModel)
                .message(userMessage)
                .build();
        try {
            MultiModalConversationResult result = conv.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text")+"";

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void clearDir(File file) {
        if(!file.isDirectory()) return;

        String[] files = file.list();
        if(files == null) return;

        for (String f: files) {
            String path = String.format("%s/%s", file.getPath(), f);
            File file1 = new File(path);
            System.out.println(file1.getName() + "---" + file1.delete());
        }

        if(file.delete()) {
            System.out.println("删除: " + file.getPath() + "成功");
        } else {
            System.err.println("删除: " + file.getPath() + "失败");
        }

    }

    private void getBasicFileInfo(String path, FileContent fileContent) {
        int position = path.lastIndexOf('/');
        String fileName = path.substring(position+1);

        try {
            BasicFileAttributes attributes = Files.readAttributes(Paths.get(path), BasicFileAttributes.class);
            long createTime = attributes.creationTime().toMillis();
            long updateTime = attributes.lastModifiedTime().toMillis();
            fileContent.setCreateDt(createTime);
            fileContent.setUpdateDt(updateTime);
            fileContent.setId(path);
            fileContent.setFilePath(path);
            fileContent.setFileName(fileName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 总结普通的文件
     */
    private void summaryNormalFileContent(String path) throws Exception {
        // 设置文件路径,请根据实际需求修改路径与文件名
        Path filePath = Paths.get(path);
        // 创建文件上传参数
        FileCreateParams fileParams = FileCreateParams.builder()
                .file(filePath)
                .purpose(FilePurpose.of("file-extract"))
                .build();

        // 上传文件打印fileid
        FileObject fileObject = openAIClient.files().create(fileParams);
        System.out.println(fileObject.id());

        // 创建聊天请求
        ChatCompletionCreateParams chatParams = ChatCompletionCreateParams.builder()
                .addSystemMessage("你是一名专业的文档摘要与信息提取专家。你的任务是从用户提供的文档中精准提取可用于向量检索的核心语义内容，同时最大限度减少冗余和成本。")
                //请将 '{FILE_ID}'替换为您实际对话场景所使用的 fileid。
                .addSystemMessage(String.format("fileid://%s", fileObject.id()))
                .addUserMessage(prompt)
                .model(model)
                .build();

        String _p = path.replaceAll(":","_").replaceAll("\\\\","_").replaceAll("/","_");
        String newPath = String.format("%s/%s.txt", outPath, _p);
        Path p = Paths.get(newPath);
        if(!Files.exists(p)) {
            try {
                Files.createFile(p);
                Files.write(p, (path+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.WRITE);
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            }
        }

        try {
            // 文件名称
            int position = path.lastIndexOf('/');
            String fileName = path.substring(position+1);
            Files.write(p, (fileName+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);

            BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
            // 创建时间
            long createTime = attributes.creationTime().toMillis();
            Files.write(p, (createTime+""+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
            // 修改时间
            long updateTime = attributes.lastModifiedTime().toMillis();
            Files.write(p, (updateTime+""+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);

        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }


        // 所有代码示例均采用流式输出，以清晰和直观地展示模型输出过程。如果您希望查看非流式输出的案例，请参见https://help.aliyun.com/zh/model-studio/text-generation
        try (StreamResponse<ChatCompletionChunk> streamResponse = openAIClient.chat().completions().createStreaming(chatParams)) {
            streamResponse.stream().forEach(chunk -> {
                // 打印每个 chunk 的内容并拼接
                String content = chunk.choices().get(0).delta().content().orElse("");
                if (!content.isEmpty()) {
//                    fullResponse.append(content);
                    try {

                        Files.write(p, content.getBytes(Charset.defaultCharset()), StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            System.out.println("success: " + newPath);
        } catch (Exception e) {
            System.err.println("错误信息：" + e.getMessage());
            System.err.println("请参考文档：https://help.aliyun.com/zh/model-studio/developer-reference/error-code");
            throw e;
        }
    }

}
