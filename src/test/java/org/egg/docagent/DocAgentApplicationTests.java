package org.egg.docagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

@SpringBootTest
class DocAgentApplicationTests {
    private VectorStore vectorStore;

    @Autowired
    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Test
    void contextLoads() {
    }

    @Test
    public void saveData() {
        /*List<Document> list = List.of(
                new Document("""
                        这篇文章主要围绕保险理赔中的“案均法”回溯分析展开，重点探讨了**人伤**和**车损**两类案件在理赔过程中的估损准确性、存在问题及改进建议。
                        
                        文章的核心内容可以概括为以下三个方面：
                        
                        ### 1. 人伤案件的案均法分析
                        *   **核心结论**：在估损准确性上，自动估损（系统）总体优于人工估损。
                            *   **死亡案件**：自动估损非常接近最终结案金额。
                            *   **伤残案件**：自动估损虽然低估，但比人工估损更准确。
                            *   **轻伤/重伤案件**：自动估损普遍偏高，但大部分案件能在3天内完成首次定损，因此高估的影响周期较短。
                        *   **问题与挑战**：目前系统对“伤情不确定”的案件统一按重伤处理，这容易导致后续定损为轻伤时出现严重高估。同时，现有规则未充分考虑伤者年龄等因素。
                        *   **数据特征**：人伤案件呈现“二八分布”，即少数伤残/死亡案件（数量占比约8%）占据了绝大部分的结案金额（占比超70%），而大量的轻伤案件（数量占比超60%）金额占比却很低（约8%）。
                        
                        ### 2. 车损案件的案均法分析
                        *   **核心结论**：整体而言，**人工估损比系统自动估损更接近最终结案金额**。
                        *   **估损偏差规律**：
                            *   **小额案件 (0-3万元)**：无论是系统还是人工，估损金额都远高于结案金额（高估），这是导致整体赔付率偏高的主要原因。
                            *   **大额案件 (>3万元)**：系统自动估损金额偏低（低估），而人工估损相对更接近。
                        *   **时效性**：从报案到车定损提交的平均周期约为3.5天，影响周期有限。
                        
                        ### 3. 具体案例分析 (Top案件分析)
                        文章通过分析多个典型的大案和非大案，深入剖析了案均法产生偏差的具体原因：
                        *   **低估案例**：
                            1.  **伤情变化**：查勘时伤情不确定（按重伤估），定损时确认为死亡，导致严重低估。
                            2.  **责任类型限制**：非机动车三者责任的人伤死亡案件，未应用更精确的赋值规则，导致低估。
                            3.  **车辆因素**：未考虑车辆全损、品牌价值或实际价值，以及多辆三者车分摊导致的案均降低。
                        *   **高估案例**：
                            1.  **地域数据缺失**：如新疆地区，因无法获取当地人均收入，系统默认取值过高（36万），导致人伤死亡案件严重高估。
                            2.  **伤情误判**：查勘时伤情不确定（按重伤估），但最终定损为轻伤，导致巨大高估。
                            3.  **索赔项关闭**：查勘时包含人伤和车损，但定损时这些索赔项被关闭，导致系统初始估损完全失效。
                            4.  **事故责任变更**：查勘后事故责任比例被下调，但初始估损未反映此变化。
                        
                        **总结来说，这篇文章是一份针对保险理赔自动化估损模型的深度复盘报告。它通过数据分析和典型案例，揭示了当前“案均法”在处理人伤和车损案件时的优势与不足，并指出了未来优化的方向，例如改进伤情判断逻辑、完善地域数据、考虑伤者年龄、调整多车分摊规则等。**
                        """)

        );

        vectorStore.add(list);*/

        List <Document> documents = List.of(
            new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!", Map.of("meta1", "meta1")),
            new Document("The World is Big and Salvation Lurks Around the Corner"),
            new Document("You walk forward facing the past and you turn back toward the future.", Map.of("meta2", "meta2")));

        // Add the documents to Milvus Vector Store
        vectorStore.add(documents);

        // Retrieve documents similar to a query
        List<Document> results = this.vectorStore.similaritySearch(SearchRequest.builder().query("Spring").topK(5).build());

    }

}
