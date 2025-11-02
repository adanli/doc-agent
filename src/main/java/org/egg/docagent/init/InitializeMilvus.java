package org.egg.docagent.init;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.CreateIndexReq;

import java.util.List;
import java.util.concurrent.TransferQueue;

/**
 * 初始化向量库
 */
public class InitializeMilvus {
    private static MilvusClientV2 client;
    private final static String COLLECTION_NAME = "vector_store";

    private static void init() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://localhost:19530")
//                .uri("http://11.0.0.191:19530")
                .build();
        client = new MilvusClientV2(config);
    }

    private static void createCollection() {
        boolean exist = client.hasCollection(
                HasCollectionReq.builder()
                        .collectionName(COLLECTION_NAME)
                        .build()
        );

        if(exist) {
            System.out.println("该collection已存在，删除");
            dropCollection();
        }

        // 创建集合
        CreateCollectionReq collectionReq = CreateCollectionReq
                .builder()
                .collectionName(COLLECTION_NAME)
                .build();

        // 添加属性
        CreateCollectionReq.CollectionSchema schema = client.createSchema();

        // id, 即文件目录
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("id")
                        .dataType(DataType.VarChar)
                        .isPrimaryKey(true)
                        .autoID(false)
                        .description("主键")
                        .build()
        );

        // 文件名
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("file_name")
                        .dataType(DataType.VarChar)
                        .description("文件名")
                        .build()
        );

        // 创建时间
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("create_dt")
                        .dataType(DataType.Int64)
                        .description("创建时间")
                        .build()
        );

        // 更新时间
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("update_dt")
                        .dataType(DataType.Int64)
                        .description("更新时间")
                        .build()
        );

        // 文件目录
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("file_path")
                        .dataType(DataType.VarChar)
                        .description("文件目录")
                        .build()
        );

        // 文件内容
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("file_content")
                        .dataType(DataType.FloatVector)
                        .dimension(1024)
                        .description("文件内容")
                        .build()
        );

        // 原始文件内容
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("source_content")
                        .dataType(DataType.VarChar)
                        .description("原始文件内容")
                        .build()
        );

        // 是否存在文件内容
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("exist_content")
                        .dataType(DataType.Bool)
                        .description("是否存在文件内容")
                        .build()
        );

        // 版本
        schema.addField(
                AddFieldReq.builder()
                        .fieldName("version")
                        .dataType(DataType.Int16)
                        .description("版本")
                        .build()
        );

        collectionReq.setCollectionSchema(schema);

        client.createCollection(collectionReq);

        System.out.println("创建collection成功");

    }

    private static void dropCollection() {
        DropCollectionReq dropCollectionReq = DropCollectionReq
                .builder()
                .collectionName(COLLECTION_NAME)
                .build();
        client.dropCollection(dropCollectionReq);
    }

    /**
     * 创建索引
     */
    private static void createIndex() {
        IndexParam indexParam = IndexParam
                .builder()
                .fieldName("file_content")
                .indexType(IndexParam.IndexType.IVF_FLAT)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        IndexParam idxFilePath = IndexParam
                .builder()
                .fieldName("file_path")
                .indexType(IndexParam.IndexType.TRIE)
                .build();

        CreateIndexReq createIndexReq = CreateIndexReq
                .builder()
                .databaseName("default")
                .collectionName(COLLECTION_NAME)
                .indexParams(List.of(indexParam, idxFilePath))
                .build();
        client.createIndex(createIndexReq);

        System.out.println("创建索引成功");
    }

    public static void main(String[] args) {
        init();
        createCollection();
        createIndex();
    }

}
