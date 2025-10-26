package org.egg.docagent.chat;

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
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {
    @Autowired
    private ChatClient chatClient;
    @Autowired
    private EmbeddingModel embeddingModel;

    private static final String DATABASE = "default";
    private static final String COLLECTION = "vector_store";

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    private String outPath = "D:\\doc_out";

    private final List<String> bashDir = List.of(
            "D:\\doc"
    );

    private final List<String> includeSuffix = List
            .of(
                    ".docx",".xlsx",".pptx",
                    ".doc",".xls",".ppt",
                    ".pdf",".txt"
            );

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

    private final Gson gson;
    private OpenAIClient openAIClient;


    {
        GsonBuilder builder = new GsonBuilder();
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gson = builder.create();

        openAIClient = OpenAIOkHttpClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .build();

    }

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
    public List<String> similarSearch(@RequestParam(value = "keywords") String keywords) {
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

        List<String> ids = results.getResults().getIds().getStrId().getDataList();
        ids.forEach(System.out::println);
        return ids;
    }

    /**
     * 遍历文件夹，处理文件
     */
    @PostMapping(value = "/execute")
    public String execute() {
        List<String> files = new ArrayList<>();
        for (String dir: bashDir) {
            iterDir(files, dir);
        }

        for (String file: files) {
            long s1 = System.currentTimeMillis();
            summaryFileContent(file);
            System.out.printf("%s文件解析完成，耗时: %ss%n", file, (System.currentTimeMillis()-s1)/1000);
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
//                String absolutePath = file.getAbsolutePath();
//                list.add(String.format("%s/%s", absolutePath, file.getName()));
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
     * 总结文章内容
     */
    @PostMapping(value = "summary-file-content")
    public String summaryFileContent(@RequestParam("path") String path) {
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
//                .addSystemMessage("fileid://{FILE_ID}")
                .addSystemMessage(String.format("fileid://%s", fileObject.id()))
                .addUserMessage("""
                        任务要求：
                        请仔细阅读以下文档内容，并执行以下操作：
                        
                        保留原始语义：确保提取的内容忠实反映原文核心信息，不添加主观解释或外部知识。
                        高度浓缩：去除重复、格式符号、页眉页脚、无关修饰语等非实质内容，仅保留对理解文档主题、关键事实、实体和逻辑关系有贡献的文本。
                        结构化输出（可选但推荐）：若文档包含明确结构（如标题、章节、列表、表格），请用简洁的自然语言将其逻辑关系保留下来（例如：“第一章：引言——介绍研究背景与目标”）。
                        输出纯文本：不要使用 Markdown、XML 或其他标记语言，仅输出干净、连贯的中文（或原文语言）段落。
                        长度控制：总输出长度应控制在原文的 20%–40% 之间（若原文极短则可接近 100%），优先保留高频关键词、专有名词、数据、结论和行动项。
                        输出格式：
                        直接输出提炼后的文本内容，不要包含任何解释、前缀（如“提炼结果：”）或后缀。
                        """)
                .model("qwen-long")
                .build();

        String _p = path.replaceAll(":","_").replaceAll("\\\\","_").replaceAll("/","_");
        String newPath = String.format("%s/%s.txt", outPath, _p);
//        String newPath = String.format("%s/%s.txt", outPath, path.replaceAll("/", "_").replaceAll(":","_").replaceAll("\\","_"));
        Path p = Paths.get(newPath);
        if(!Files.exists(p)) {
            try {
                Files.createFile(p);
                Files.write(p, (path+'\n').getBytes(Charset.defaultCharset()), StandardOpenOption.WRITE);
            } catch (IOException e) {
                e.printStackTrace();
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
        }


        // 所有代码示例均采用流式输出，以清晰和直观地展示模型输出过程。如果您希望查看非流式输出的案例，请参见https://help.aliyun.com/zh/model-studio/text-generation
        try (StreamResponse<ChatCompletionChunk> streamResponse = openAIClient.chat().completions().createStreaming(chatParams)) {
            streamResponse.stream().forEach(chunk -> {
                // 打印每个 chunk 的内容并拼接
//                System.out.println(chunk);
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
            return "success";
        } catch (Exception e) {
            System.err.println("错误信息：" + e.getMessage());
            System.err.println("请参考文档：https://help.aliyun.com/zh/model-studio/developer-reference/error-code");
        }

        System.err.println("解析文件失败: " + path);
        return null;

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

    }

}
