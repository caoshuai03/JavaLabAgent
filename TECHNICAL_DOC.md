# JavaLab Agent 技术文档

## 一、项目背景与目标

学生在使用校内实验教学/作业平台时，经常会遇到重复性问题（实验要求不清、报告格式、OJ 平台使用等），从而频繁咨询老师。

**Java 实验辅助 Agent** 是面向 Java 实验教学场景的 RAG（检索增强生成）智能问答系统，核心目标：

- 减少重复咨询成本：将高频问题沉淀到私有知识库，形成事实驱动回答。
- 提高回答可信度：优先基于知识库检索结果回答，并在无命中时使用外部大模型兜底。
- 私有化部署：支持本地大模型（Ollama）与私有向量数据库（PostgreSQL + pgvector）。

项目覆盖的知识来源可包含（按实际运营逐步沉淀）：

- 实验教材/实验指导书
- 课程讨论区常见问题与老师答复
- 视频课程/平台说明（如 Ccode/类似平台的使用说明）

---

## 二、项目结构与模块概览

仓库根目录核心结构：

- `javalab-agent-back/`
  - Spring Boot 后端（RAG、会话管理、知识库入库、鉴权、文件存储等）
- `javalab-agent-front/`
  - Vue 3 前端（对话 UI、知识库管理页面等）
- `sql/init.sql`
  - PostgreSQL 初始化脚本：pgvector 扩展、向量表、会话表、消息表等
- `docker-compose.yml` / `docker-compose.prod.yml`
  - 一键部署编排（前端 + 后端 + PostgreSQL(pgvector) + MinIO）

---

## 三、快速开始（Docker）

### 3.1 环境变量

根目录创建 `.env`（参考 `README.md`）：

```
OLlama_BASE_URL=http://xxx:11434
IMAGE_TAG=2026012201
```

- `OLlama_BASE_URL`：Ollama 服务地址（例如 `http://localhost:11434`）
- PostgreSQL 可使用默认账号/密码，也可通过环境变量覆盖
- `IMAGE_TAG`=2026012201：镜像tag

### 3.2 Docker Compose 启动

生产镜像版本方式：

```bash
docker compose -f docker-compose.prod.yml up -d
```

启动后常用访问地址（默认）：

- 前端：`http://localhost`
- 后端：`http://localhost:8989`
- MinIO 控制台：`http://localhost:9001`
- PostgreSQL：`http://localhost:5432`

---

## 四、会话记忆管理

本项目的会话记忆采用**数据库持久化 + 滑动窗口**的实现方案，可保障：保留最近 10 条历史对话记录，每次对话均做持久化处理，消息类型分为 user、role、assistant 三类；支持跨请求 / 跨刷新恢复上下文；实现用户隔离和会话隔离。

### 4.1 滑动窗口上下文

入口：`RagServiceImpl#chat()` → `ChatMessageServiceImpl#getRecentMessages()`

处理流程为：先将用户消息保存至`chat_message`表，再查询最近`MEMORY_SIZE`条消息作为对话上下文，最后将数据库中的消息转换为 Spring AI `Message`列表，并按用户 / 助手消息成对组织。

**关键配置常量**：`RagConstant.MEMORY_SIZE`（当前为 10）

---

## 五、RAG 检索增强

本项目 RAG 分为两条主链路：

- **知识库入库链路**：将文档切分并写入 pgvector
- **在线问答检索链路**：对用户问题检索相关片段，拼接到提示词中再请求大模型

### 5.1 RAG 知识库入库流程

**用QA对拆分，保证QA对完整性，提升回答准确率。**

入口接口：`KnowledgeController#upload()`

处理流程：

1. **文档解析**
   - 使用 `TikaDocumentReader` 从 `MultipartFile` 中抽取文本
   - 优点：支持多种文件格式（doc/docx/pdf/文本等，视 Tika 能力）
2. **文档切分（Chunking）**
   - 若检测为 QA 格式（同时包含 `---` 与 `## Q:`）：使用 `QaDocumentSplitter` 按 QA 对切分
   - 否则：使用 `TokenTextSplitter` 按 token 规模切分
3. **向量化存储（Embedding + VectorStore）**
   - 调用 `vectorStore.add(splitDocuments)` 写入向量库
   - 向量维度与 embedding 模型保持一致（当前为 1024）
4. **原始文件存储**
   - 通过 `StorageUtil` 上传到 `MinIO` 或 `Aliyun OSS`（由 `STORAGE_TYPE` 决定）
5. **文件记录落库**
   - 写入 `ali_oss_file`：包含 `file_name`、`url`、`vector_id`（分片 Document 的 id 列表）

**失败处理策略**：

- 若向量化失败，会抛出运行时异常并阻断后续步骤（避免“文件上传成功但向量缺失”的不一致状态）

### 5.2 向量库与索引

向量存储基于 PostgreSQL + pgvector 实现，核心配置如下：

1. 索引类型：采用 HNSW 索引，兼顾检索效率与召回精度；
2. 距离度量：使用余弦距离计算向量相似度，适配文本语义匹配场景；
3. 向量维度：1024 维，与当前使用的 embedding 模型输出维度对齐。

### 5.3 提示词策略

提示词文件：`javalab-agent-back/src/main/resources/prompts/chat-default.md`

关键点：

1. 优先级规则：强制遵循 “知识库优先” 原则，优先基于检索到的知识库内容生成回答；
2. 输出规范：命中知识库内容时，回答前缀需标注「【根据知识库】：」；无匹配内容时标注「【根据通用知识】：」；
3. 领域限制：限定回答范围为 Java 技术 / 实验相关问题，过滤无关领域提问。

---

## 六、本地大模型集成

本项目通过 Ollama 本地部署了 **qwen3:8b** 对话大模型与 **gte-large-zh** 向量嵌入模型。

项目基于 **Spring AI** 快速实现 Ollama 集成，核心要点如下：

1. 依赖：`spring-ai-ollama-spring-boot-starter`
2. 配置：`application.yml` → `spring.ai.ollama.*`

### 6.1 模型配置

1. 对话模型（Chat Model）：`qwen3:8b`
2. 嵌入模型（Embedding Model）：`turingdance/gte-large-zh:latest`

嵌入模型的输出向量维度，必须与 pgvector 数据表及项目配置保持一致（当前为 1024 维），确保向量检索的有效性；

---

## 七、未来优化方向

### 7.1 工具集成

集成本地工具和MCP工具，提升Agent能力和回答准确性。

### 7.2 会话记忆升级

- 当前为滑动窗口“短记忆”，可引入：
  - “对话摘要记忆”（定期总结历史上下文，减少 token 消耗）
  - “对话语义检索”（将历史消息向量化后按相似度检索相关历史）

### 7.3 多模型/容灾策略

- 模型选择策略：根据问题类型路由（命中知识库走本地小模型，未命中走外部大模型）。
- 超时/重试/熔断：对流式输出增加超时与错误兜底响应。

### 7.4 知识库运营与质量控制

- 增量更新：同文件多次上传时支持覆盖/版本管理，并同步清理旧向量。
- 评价闭环：收集用户反馈（好/差、纠错），反向驱动知识库更新。

---

## 八、附录：关键接口速查

- RAG 对话（SSE）：`POST /api/v1/ai/rag`
- 查询会话历史：`POST /api/v1/ai/rag/history`
- 查询会话列表：`POST /api/v1/ai/rag/sessions`
- 删除会话（逻辑删除）：`POST /api/v1/ai/rag/sessions/delete`

- 上传知识库文件：`POST /api/v1/knowledge/file/upload`（multipart）
- 查询文件：`GET /api/v1/knowledge/contents`
- 删除文件：`DELETE /api/v1/knowledge/delete`
- 下载文件（批量）：`GET /api/v1/knowledge/download`
- 下载文件（流）：`GET /api/v1/knowledge/downloadFile/{id}`
