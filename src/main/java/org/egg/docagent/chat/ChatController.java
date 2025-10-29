package org.egg.docagent.chat;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.*;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.highlevel.dml.InsertRowsParam;
import org.egg.docagent.entity.FileContent;
import org.egg.docagent.ossutil.OSSUtil;
import org.egg.docagent.pdf2image.PPTToImageConverter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@PropertySource(value = "classpath:application.properties", encoding = "UTF-8")
public class ChatController implements InitializingBean {
    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;
    @Value("${spring.ai.openai.api-key}")
    private String sk;
    @Value("${file.out.path}")
    private String outPath;
    @Value("${file.base.path}")
    private String basePath;
    @Value("${spring.ai.openai.chat.options.model}")
    private String model;

    @Autowired
    private ChatClient chatClient;
    @Autowired
    private EmbeddingModel embeddingModel;
    @Autowired
    private OSSUtil ossUtil;

    private static final String DATABASE = "default";
    private static final String COLLECTION = "vector_store";

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    private final List<String> baseDir = new ArrayList<>();

    private final List<String> includeSuffix = List
            .of(
                    ".docx",".xlsx",".pptx",
                    ".doc",".xls",".ppt",
                    ".pdf",".txt", ".xmind",
                    ".pptx"
            );

    private static final String PROMPT = """
            任务要求：
                请仔细阅读以下文档内容，并执行以下操作：

                保留原始语义：确保提取的内容忠实反映原文核心信息，不添加主观解释或外部知识。
                高度浓缩：去除重复、格式符号、页眉页脚、无关修饰语等非实质内容，仅保留对理解文档主题、关键事实、实体和逻辑关系有贡献的文本。
                结构化输出（可选但推荐）：若文档包含明确结构（如标题、章节、列表、表格），请用简洁的自然语言将其逻辑关系保留下来（例如：“第一章：引言——介绍研究背景与目标”）。
                输出纯文本：不要使用 Markdown、XML 或其他标记语言，仅输出干净、连贯的中文（或原文语言）段落。
                长度控制：总输出长度应控制在原文的 20%–40% 之间（若原文极短则可接近 100%），优先保留高频关键词、专有名词、数据、结论和行动项。
                输出格式：
                直接输出提炼后的文本内容，不要包含任何解释、前缀（如“提炼结果：”）或后缀。
            """;

    @GetMapping("/ai")
    public Map<String, String> completion(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message, @RequestParam(value = "voice") String voice) {
        return Map.of(
                "completion",
                chatClient.prompt()
                        .system(sp -> sp.param("voice", voice))
                        .user(message)
                        .call()
                        .content()
        );
    }

    @Autowired
    private MilvusServiceClient milvusServiceClient;

    private Gson gson;
    private OpenAIClient openAIClient;

    @GetMapping(value = "/save-info-milvus")
    public String saveIntoMilvus(String path) {
        FileContent fileContent = getFileContent(path);

        List<JsonObject> list = new ArrayList<>();
        list.add(gson.fromJson(gson.toJson(fileContent), JsonObject.class));

        InsertRowsParam insertRowsParam = InsertRowsParam.newBuilder()
                .withCollectionName("vector_store")
                .withRows(list)
                .build();

        milvusServiceClient.insert(insertRowsParam);

        return "success";
    }

    private FileContent getFileContent(String path) {
        try {
            List<String> list = Files.readAllLines(Paths.get(path));
            if(CollectionUtils.isEmpty(list)) throw new RuntimeException(path + "文件为空");

            if(list.size() < 4) throw new RuntimeException(path + "行数过少");

            StringBuilder sb = new StringBuilder();

            String filePath = list.get(0);
            sb.append("文件路径: ").append(filePath).append('\n');
            String fileName = list.get(1);
            sb.append("文件名: ").append(fileName).append('\n');
            String createTime = list.get(2);
            sb.append("创建时间: ").append(sdf.format(new Date(Long.parseLong(createTime)))).append('\n');
            String updateTime = list.get(3);
            sb.append("更新时间: ").append(sdf.format(new Date(Long.parseLong(updateTime)))).append('\n');

            FileContent fileContent = new FileContent();
            fileContent.setId(filePath);
            fileContent.setFileName(fileName);
            fileContent.setCreateDt(Long.valueOf(createTime));
            fileContent.setUpdateDt(Long.valueOf(updateTime));
            fileContent.setFilePath(filePath);

            if(list.size() > 4) {
                for (int i=4; i<list.size(); i++){
                    sb.append(list.get(i));
                }
                fileContent.setExistContent(true);
            } else {
                fileContent.setExistContent(false);
            }

            String content = sb.toString();
            float[] embedding = this.embedding(content);
            fileContent.setFileContent(embedding);
            fileContent.setSourceContent(content);

            return fileContent;

        } catch (IOException e) {
            e.printStackTrace();
        }

        throw new RuntimeException("解析文件失败" + path);
    }

    private float[] embedding(String content) {
        EmbeddingResponse response = this.embeddingModel.embedForResponse(List.of(content));
        return response.getResult().getOutput();
    }

    /**
     * 根据关键字模糊查询
     * @return 返回模糊查询匹配成功的主键列表
     */
    @GetMapping(value = "/similar-search")
    public List<SearchResult> similarSearch(@RequestParam(value = "keywords") String keywords) {
        float[] embeddings = this.embedding(keywords);
        List<List<Float>> queryVectors = new ArrayList<>();
        List<Float> vector = new ArrayList<>();
        for (float embedding : embeddings) {
            vector.add(embedding);
        }
        queryVectors.add(vector);

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withDatabaseName(DATABASE)
                .withTopK(10)
                .withVectorFieldName("file_content")
                .withMetricType(MetricType.COSINE)
                .withFloatVectors(queryVectors)
                .build();

        R<SearchResults> response = milvusServiceClient.search(searchParam);
        SearchResults results = response.getData();

        int size = results.getResults().getIds().getStrId().getDataCount();
        List<SearchResult> list = new ArrayList<>(size);

        for (int i=0; i<size; i++) {
            float score = results.getResults().getScores(i);
            String id = results.getResults().getIds().getStrId().getData(i);
            System.out.printf("%s ----- %f%n", id, score);
            list.add(new SearchResult(id, score));
        }

        return list;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        GsonBuilder builder = new GsonBuilder();
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gson = builder.create();

        openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(sk)
                .baseUrl(String.format("%s/%s", baseUrl, "v1"))
                .build();

        baseDir.add(basePath);
    }

    record SearchResult(String id, float score){}

    /**
     * 遍历文件夹，处理文件
     */
    @PostMapping(value = "/execute")
    public String execute() {
        List<String> files = new ArrayList<>();
        for (String dir: baseDir) {
            iterDir(files, dir);
        }

        int total = files.size();
        int successCount = 0;
        int errorCount = 0;

        for (String file: files) {
            long s1 = System.currentTimeMillis();
            try {
                summaryFileContent(file);
                successCount++;
            } catch (Exception e) {
                e.printStackTrace();
                errorCount++;
            }
            System.out.printf("%s文件解析完成，耗时: %ss，成功进度: %d/%d, 失败进度: %d/%d%n", file, (System.currentTimeMillis()-s1)/1000, successCount, total, errorCount, total);
        }

        return "success";
    }

    public void iterDir(List<String> list, String dir) {
        File file = new File(dir);
        if(file.isDirectory()) {
            String[] dirs = file.list();
            if(dirs != null) {
                for (String s : dirs) {
                    iterDir(list, String.format("%s/%s", dir, s));
                }
            }
        } else {
            if(isIncludeFile(file.getName())) {
                list.add(dir);
            }
        }
    }

    private boolean isIncludeFile(String file) {
        for (String suffix: includeSuffix) {
            if (file.endsWith(suffix)) {
                return true;
            }
        }
        return false;
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
                .addUserMessage(PROMPT)
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

    /**
     * 总结文章内容
     */
    @PostMapping(value = "summary-file-content")
    public String summaryFileContent(@RequestParam("path") String path) throws Exception{
        if(path.endsWith(".pptx")) {
            this.summaryPPTFileContent(path);
        } else {
            this.summaryNormalFileContent(path);
        }
        return "success";
    }

    /**
     * 1. 将PPT转成图片
     * 2. 使用大模型，解析图片
     * 3. 将文件夹内图片，解析后合并到一个文件
     */
    private void summaryPPTFileContent(String path) {
        String _p = path.replaceAll(":","_").replaceAll("\\\\","_").replaceAll("/","_");
        String format = "png";
        String outputDir = String.format("%s/%s_dir", outPath, _p);
        File file = new File(outputDir);
        if(!file.exists()) {
            file.mkdirs();
        }

        PPTToImageConverter.convertToImages(path, outputDir, format);

        StringBuilder sb = new StringBuilder();
        String[] files = file.list();
        FileContent fileContent = new FileContent();
        this.getBasicFileInfo(path, fileContent);

        for(String f: files) {
            String p = String.format("%s/%s", outputDir, f);
            // 上传到oss
            try {
                ossUtil.upload(new FileInputStream(p), f);
            } catch (Exception e) {
                throw new RuntimeException("上传到oss失败: " + f);
            }

            String content = this.summaryPicture(f);
            sb.append(content);
        }
        fileContent.setSourceContent(sb.toString());

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

        try {

            Path p = Paths.get(outputFile);
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
     * 读取文件，保存到向量数据库中
     */
    @PostMapping(value = "/save-info-milvus2")
    public void saveIntoMilvus2() {
        File file = new File(outPath);
        if(!file.isDirectory()) return;

        String[] files = file.list();
        if(files == null) return;
        System.out.printf("合计文件数量: %d%n", files.length);

        int count = 0;
        for (String f: files) {
            try {
                this.saveIntoMilvus(String.format("%s/%s", outPath, f));
                count++;
                System.out.printf("完成%d/%d: %s%n", count, files.length, f);
            } catch (Exception e) {
                System.err.printf("异常%d/%d: %s%n", count, files.length, f);
            }
        }

        System.out.println("完成");

    }

    /**
     * 识别图片
     */
    @PostMapping(value = "/summary-picture")
    public String summaryPicture(@RequestParam("path") String path) {
        String imagePath = ossUtil.url(path);

        MultiModalConversation conv = new MultiModalConversation();
        MultiModalMessage userMessage = MultiModalMessage.builder().role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", imagePath),
                        Collections.singletonMap("text", PROMPT))).build();
        MultiModalConversationParam param = MultiModalConversationParam.builder()
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apiKey("sk-xxx")
                .apiKey(sk)
                .model(model)
                .message(userMessage)
                .build();
        try {
            MultiModalConversationResult result = conv.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text")+"";

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
