package com.cs.rag.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Title: QaDocumentSplitter
 * @author caoshuai
 * @date 2025/12/01
 * @description: 自定义QA文档分割器
 * 
 *               用于处理特定格式的Markdown QA文档：
 *               - 每个QA对之间使用 "---" 分隔符
 *               - 问题部分以 "## Q:" 开头
 *               - 答案部分以 "A:" 开头
 * 
 *               该分割器会将文档按QA对进行分割，并保留完整的问答上下文
 */
@Slf4j
public class QaDocumentSplitter {

    // QA分隔符
    private static final String QA_SEPARATOR = "---";
    // 问题标识
    private static final String QUESTION_PREFIX = "## Q:";
    // 答案标识
    private static final String ANSWER_PREFIX = "A:";

    /**
     * 将包含多个QA对的文档分割成独立的Document对象列表
     * 
     * @param documents 原始文档列表（通常来自TikaDocumentReader）
     * @return 分割后的Document列表，每个Document包含一个完整的QA对
     */
    public static List<Document> split(List<Document> documents) {
        List<Document> splitDocuments = new ArrayList<>();

        // 遍历每个输入文档
        for (Document doc : documents) {
            String content = doc.getText();
            log.info("开始处理文档，总长度: {} 字符", content.length());

            // 按分隔符拆分文档内容
            String[] qaBlocks = content.split(QA_SEPARATOR);
            log.info("按 '---' 分隔符拆分，得到 {} 个块", qaBlocks.length);

            // 处理每个QA块
            for (int i = 0; i < qaBlocks.length; i++) {
                String block = qaBlocks[i].trim();

                // 跳过空块和标题块（不包含Q:的块）
                if (block.isEmpty() || !block.contains(QUESTION_PREFIX)) {
                    log.debug("跳过第 {} 个块（空块或标题块）", i + 1);
                    continue;
                }

                // 解析QA对
                QaPair qaPair = parseQaPair(block);
                if (qaPair != null && qaPair.isValid()) {
                    // 创建新的Document对象
                    Document qaDocument = createQaDocument(qaPair, doc, i);
                    splitDocuments.add(qaDocument);

                    log.info("成功解析第 {} 个QA对: {}",
                            splitDocuments.size(),
                            qaPair.getQuestion().substring(0, Math.min(50, qaPair.getQuestion().length())));
                } else {
                    log.warn("第 {} 个块解析失败，跳过", i + 1);
                }
            }
        }

        log.info("文档分割完成，共生成 {} 个QA Document", splitDocuments.size());
        return splitDocuments;
    }

    /**
     * 解析单个QA块，提取问题和答案
     * 
     * @param block QA文本块
     * @return QaPair对象，包含问题和答案
     */
    private static QaPair parseQaPair(String block) {
        try {
            // 查找问题起始位置
            int questionStart = block.indexOf(QUESTION_PREFIX);
            if (questionStart == -1) {
                log.warn("未找到问题标识 '{}'", QUESTION_PREFIX);
                return null;
            }

            // 查找答案起始位置
            int answerStart = block.indexOf(ANSWER_PREFIX);
            if (answerStart == -1) {
                log.warn("未找到答案标识 '{}'", ANSWER_PREFIX);
                return null;
            }

            // 提取问题内容（去除 "## Q:" 前缀）
            String question = block.substring(questionStart + QUESTION_PREFIX.length(), answerStart).trim();

            // 提取答案内容（去除 "A:" 前缀）
            String answer = block.substring(answerStart + ANSWER_PREFIX.length()).trim();

            return new QaPair(question, answer);

        } catch (Exception e) {
            log.error("解析QA块时发生异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 创建包含QA内容的Document对象
     * 
     * @param qaPair      QA对
     * @param originalDoc 原始文档（用于继承元数据）
     * @param index       QA对的索引
     * @return Document对象
     */
    private static Document createQaDocument(QaPair qaPair, Document originalDoc, int index) {
        // 构建完整的QA内容（问题+答案）
        String qaContent = "问题: " + qaPair.getQuestion() + "\n\n答案: " + qaPair.getAnswer();

        // 创建元数据
        Map<String, Object> metadata = new HashMap<>();

        // 继承原始文档的元数据
        if (originalDoc.getMetadata() != null) {
            metadata.putAll(originalDoc.getMetadata());
        }

        // 添加QA特定的元数据
        metadata.put("qa_index", index); // QA对的索引
        metadata.put("question", qaPair.getQuestion()); // 单独存储问题，便于后续检索
        metadata.put("content_type", "qa_pair"); // 标识这是一个QA对文档

        // 创建并返回Document对象
        return new Document(qaContent, metadata);
    }

    /**
     * QA对内部类，用于存储问题和答案
     */
    private static class QaPair {
        private final String question;
        private final String answer;

        public QaPair(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }

        public String getQuestion() {
            return question;
        }

        public String getAnswer() {
            return answer;
        }

        /**
         * 检查QA对是否有效（问题和答案都不为空）
         */
        public boolean isValid() {
            return question != null && !question.trim().isEmpty()
                    && answer != null && !answer.trim().isEmpty();
        }
    }
}
