package org.egg.docagent.chat;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.egg.docagent.ossutil.OSSUtil;
import org.egg.docagent.pdf2image.PDFToImageConverter;
import org.egg.docagent.ppt2image.PPTToImageConverter;
import org.egg.docagent.word2image.WordToImageConverter;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.Thread;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public class ChatExecute implements Runnable{
    private static final String DATABASE = "default";
    private static final String COLLECTION = "vector_store";

    private final List<String> files;
    private final AtomicInteger successCount;
    private final AtomicInteger errorCount;
    private final AtomicInteger skipCount;

    private final String prompt;
    private OpenAIClient openAIClient;
    private final String outPath;
    private MinioClient minioClient;

    private final String sk;
    private final String picModel;
    private final String model;

    private final MilvusServiceClient milvusServiceClient;
    private final CountDownLatch latch;
    private final String baseUrl;

    private final String minioEndpoint;
    private final String minioAccessKey;
    private final String minioSecretKey;
    private final String bucket;

    private int success = 0;
    private int error = 0;
    private int skip = 0;

    private ChatModel chatModel;

    private RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
//    private String url = "http://1414587873548331.cn-shanghai-finance-1.pai-eas.aliyuncs.com/api/predict/qwen25_vl_32b/v1/chat/completions";
    private String url = "http://1414587873548331.cn-shanghai-finance-1.pai-eas.aliyuncs.com/api/predict/qwen3_vl_32b/v1/chat/completions";

    private OSSUtil ossUtil;

    public ChatExecute(List<String> files, AtomicInteger successCount, AtomicInteger errorCount, AtomicInteger skipCount, String prompt, String outPath, String sk, String picModel, String model, CountDownLatch latch,
        String baseUrl,
        String minioEndpoint,
        String minioAccessKey,
        String minioSecretKey,
        String bucket,
        MilvusServiceClient milvusServiceClient,
        ChatModel chatModel,
        OSSUtil ossUtil

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
        this.chatModel = chatModel;
        this.ossUtil = ossUtil;

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


        try {

            /*minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket(bucket)
                            .filename(filePath)
                            .object(uploadPath)
                            .build()
            );*/

            ossUtil.upload(new FileInputStream(filePath), uploadPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取URL
     */
    public String url(String path) {
        try {
            /*return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(path)
                            .expiry(300)
                            .method(Method.GET)
                    .build());*/

            return ossUtil.url(path);

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
            /*minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(path)
                    .build());*/

            ossUtil.delete(path);

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
                    skip++;
                    continue;
                }

                summaryFileContent(file);
                successCount.incrementAndGet();
                success++;
            } catch (Exception e) {
                e.printStackTrace();
                errorCount.incrementAndGet();
                error++;
            }
            System.out.printf("%s---%s文件解析完成，耗时: %ss，成功进度: %d/%d, 失败进度: %d/%d, 跳过进度: %d/%d%n", Thread.currentThread().getName(), file, (System.currentTimeMillis()-s1)/1000, success, files.size(), error, files.size(), skip, files.size());
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

    private void summaryFileContent(@RequestParam("path") String path) throws Exception{
        if(path.endsWith(".pptx") || path.endsWith(".docx") || path.endsWith(".pdf")) {
            this.summaryFileContentWithPic(path);
        }   else {
            this.summaryNormalFileContent(path);
        }
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

                Files.writeString(p, fileContent.getFilePath()+'\n', Charset.defaultCharset(), StandardOpenOption.WRITE);
                // 文件名称
                Files.writeString(p, fileContent.getFileName()+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);

                // 创建时间
                Files.writeString(p, fileContent.getCreateDt()+""+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);
                // 修改时间
                Files.writeString(p, fileContent.getUpdateDt()+""+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);

                if(StringUtils.hasLength(fileContent.getSourceContent())) {
                    Files.writeString(p, fileContent.getSourceContent(), Charset.defaultCharset(), StandardOpenOption.APPEND);
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

                String url = this.url(nf);
                String content = this.summaryPicture2(url);
//                sb.append(content);
                try {
                    Files.writeString(p, content+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                this.delete(nf);
            }
//            fileContent.setSourceContent(sb.toString());

        } finally {
            clearDir(file);
        }


    }

    private void summaryFileContentWithPic2(String path) {
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

                Files.writeString(p, fileContent.getFilePath()+'\n', Charset.defaultCharset(), StandardOpenOption.WRITE);
                // 文件名称
                Files.writeString(p, fileContent.getFileName()+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);

                // 创建时间
                Files.writeString(p, fileContent.getCreateDt()+""+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);
                // 修改时间
                Files.writeString(p, fileContent.getUpdateDt()+""+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);

                if(StringUtils.hasLength(fileContent.getSourceContent())) {
                    Files.writeString(p, fileContent.getSourceContent(), Charset.defaultCharset(), StandardOpenOption.APPEND);
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            for(String f: files) {
                // 区分线程
                String nf = String.format("%s/%s", Thread.currentThread().getName(), f);

                String pf = String.format("%s/%s", outputDir, f);
                // 上传到oss
                /*try {
                    this.upload(pf, nf);
                } catch (Exception e) {
                    throw new RuntimeException("上传到oss失败: " + nf);
                }*/

//                String content = this.summaryPicture(nf);
                String content = this.summaryPictureDirect(pf);
                if(StringUtils.hasLength(content)) {
                    try {
                        Files.writeString(p, content+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

//                this.delete(nf);
            }


        } finally {
            clearDir(file);
        }


    }

    private String summaryPictureDirect(String path){
        Media imageMedia = null;
        try {
            imageMedia = new Media(
                    MediaType.IMAGE_PNG,
                new InputStreamResource(new FileInputStream(path))
            );
        } catch (FileNotFoundException e) {
            System.err.println("读取图片失败");
            return null;
        }

        // 构建多模态消息
        UserMessage userMessage = UserMessage.builder()
                .text(prompt)
                .media(imageMedia)
                .build();

        Prompt prompt = new Prompt(userMessage);
        ChatResponse response = chatModel.call(prompt);
        return response.getResult().getOutput().getText();

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
                Files.writeString(p, path+'\n', Charset.defaultCharset(), StandardOpenOption.WRITE);
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            }
        }

        try {
            // 文件名称
            int position = path.lastIndexOf('/');
            String fileName = path.substring(position+1);
            Files.writeString(p, fileName+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);

            BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
            // 创建时间
            long createTime = attributes.creationTime().toMillis();
            Files.writeString(p, createTime+""+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);
            // 修改时间
            long updateTime = attributes.lastModifiedTime().toMillis();
            Files.writeString(p, updateTime+""+'\n', Charset.defaultCharset(), StandardOpenOption.APPEND);

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

                        Files.writeString(p, content, Charset.defaultCharset(), StandardOpenOption.APPEND);
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

    public String summaryPicture2(@RequestParam("picPath") String picPath) {
//        picPath = "http://prod-basicmodel-oss.oss-cn-shanghai-finance-1-internal.aliyuncs.com/coverage_assistant/data/page_019.png?OSSAccessKeyId=LTAI5tMngzLoZ4ndmU1k2iWK&Expires=1762266541&Signature=N3NLaM7rpAgTBJ6L8tpHe14ZJ7g%3D";
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("model", "");

            List<Map<String, Object>> paramList = new ArrayList<>();
            params.put("messages", paramList);

            Map<String, Object> paramMsg = new HashMap<>();
            paramList.add(paramMsg);

            paramMsg.put("role", "user");
            List<Map<String, Object>> listContent = new ArrayList<>();
            paramMsg.put("content", listContent);

            Map<String, Object> mapContent1 = new HashMap();
            Map<String, Object> mapContent2 = new HashMap();
            listContent.add(mapContent1);
            listContent.add(mapContent2);

            mapContent1.put("type", "image_url");

            Map<String, String> mapUrl = new HashMap<>();
            mapContent1.put("image_url", mapUrl);
            mapUrl.put("url", picPath);


            mapContent2.put("type", "text");
            mapContent2.put("text", """
                    任务要求：请仔细阅读以下文档内容，并执行以下操作：保留原始语义：确保提取的内容忠实反映原文核心信息，不添加主观解释或外部知识。高度浓缩：去除重复、格式符号、页眉页脚、无关修饰语等非实质内容，仅保留对理解文档主题、关键事实、实体和逻辑关系有贡献的文本。结构化输出（可选但推荐）：若文档包含明确结构（如标题、章节、列表、表格），请用简洁的自然语言将其逻辑关系保留下来（例如：“第一章：引言——介绍研究背景与目标”）。输出纯文本：不要使用 Markdown、XML 或其他标记语言，仅输出干净、连贯的中文（或原文语言）段落。长度控制：总输出长度应控制在150字以内，优先保留高频关键词、专有名词、数据、结论和行动项。输出格式：直接输出提炼后的文本内容，不要包含任何解释、前缀（如“提炼结果：”）或后缀。
                    """);



            MultiValueMap<String, String> headers = new HttpHeaders();
//            headers.add("Authorization", "ZGU2N2EwOWE0MmI0ZGEyNmNjNmE5NTc1YTBhN2MxOTAyYjNlYzAxYw==");
            headers.add("Authorization", "Zjk5MzgyMzg4YTA5MTAxNjIwNGNjNWFiOGIyMjQyOGRjYTdjZDE1YQ==");
            headers.add("Content-Type", "application/json");

            RequestEntity<String> request = new RequestEntity<>(mapper.writeValueAsString(params), headers, HttpMethod.POST, URI.create(url));
            ResponseEntity<String> response = restTemplate.exchange(request, String.class);
//            System.out.println(response);

//            System.out.println((((Map)((Map)((List)mapper.readValue(response.getBody(), Map.class).get("choices")).get(0)).get("message")).get("content")));

//            return response.getBody();
            return ((String) (((Map)((Map)((List)mapper.readValue(response.getBody(), Map.class).get("choices")).get(0)).get("message")).get("content"))).trim();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
