# JavaLab Agent 技术文档

## 一、项目背景与目标

学生在使用校内实验教学 / 作业平台时，经常会遇到重复性问题（实验要求不清、报告格式、OJ 平台使用等），从而频繁咨询老师。

**JavaLab Agent** 是面向 Java 实验教学场景的智能助手系统，从最初的 RAG 问答出发，逐步演进为集成 **RAG**、**ReAct Agent**、**MCP 工具**、**Skills 专业技能** 的综合 Agent 平台。核心目标：

- **减少重复咨询成本**：高频问题沉淀到私有知识库，形成事实驱动回答；
- **提高回答可信度**：优先基于知识库与工具结果回答，并在缺失时调用通用知识兜底；
- **私有化部署**：支持本地大模型（Ollama）与私有向量数据库（PostgreSQL + pgvector），可完全离线运行；
- **可扩展性**：通过 MCP 协议接入外部工具，通过 Skills 模块定制专业领域行为。

项目覆盖的知识来源可包含（按实际运营逐步沉淀）：

- 实验教材 / 实验指导书
- 课程讨论区常见问题与老师答复
- 视频课程 / 平台说明（如 Ccode/类似平台的使用说明）

---

## 二、整体架构

### 2.1 系统分层

```
┌──────────────────────────────────────────────────┐
│  前端 (Vue 3 + Element Plus + Vite)               │
│  - 对话 UI / 知识库管理 / Skills / MCP 设置        │
└──────────────────────┬───────────────────────────┘
                       │ HTTP + SSE
┌──────────────────────▼───────────────────────────┐
│  后端 (Spring Boot 3.4 + Spring AI)               │
│                                                  │
│  Controller 层  ── 鉴权拦截器 (JWT)               │
│  Service 层                                       │
│   ├─ AskService        普通 RAG 流式问答          │
│   ├─ AgentService      ReAct Plan/Act/Observe    │
│   ├─ ToolService       内置工具 + MCP 工具汇聚    │
│   ├─ SkillService      Skills 加载与命中匹配      │
│   ├─ McpService        MCP stdio/http/sse        │
│   ├─ KnowledgeService  文档切分 / 向量化 / 检索   │
│   ├─ SummaryService    会话滚动摘要                │
│   └─ ChatSession/MessageService  会话与消息持久化  │
│  存储抽象  StorageUtil (MinIO / 阿里云 OSS)        │
└──────┬─────────────────┬─────────────────────────┘
       │                 │
┌──────▼─────┐  ┌────────▼────────┐  ┌────────────┐
│ PostgreSQL │  │   MinIO / OSS   │  │  Ollama /  │
│ + pgvector │  │   对象存储       │  │  千帆 LLM  │
└────────────┘  └─────────────────┘  └────────────┘
```

### 2.2 模块职责

| 模块 | 主要类 | 说明 |
| :--- | :--- | :--- |
| ReAct Agent | `AgentServiceImpl` | Plan / Act / Observe 多轮循环，工具调度 |
| 工具汇聚 | `ToolServiceImpl` | 内置 5 个工具 + MCP 工具，统一执行入口 |
| MCP 协议 | `McpServiceImpl` | stdio / http / sse 三模式，工具列表缓存 |
| 普通 RAG | `AskServiceImpl` | 检索召回 + 单轮 LLM 回答 |
| 共享支撑 | `RagConversationSupport` | RAG/Agent 共用：召回过滤、摘要、模型选择 |
| 知识库 | `KnowledgeServiceImpl` | 文档切分、向量化、文件落库 |
| Skills | `SkillServiceImpl` | 启动加载 SKILL.md，按关键词命中注入 |
| 摘要 | `SummaryServiceImpl` | 滚动摘要生成与持久化 |
| 鉴权 | `JwtTokenUserInterceptor` / `AdminInterceptor` | 用户与管理员双拦截器 |
| 存储 | `StorageUtil` (`MinioUtil` / `AliOssUtil`) | `storage.type` 切换实现 |

---

## 三、快速开始（Docker）

### 3.1 环境变量

根目录创建 `.env`（参考 `README.md`）。最关键的几项：

```env
OLlama_BASE_URL=http://xxx:11434          # Ollama 服务地址
OPENAI_API_KEY=your_openai_api_key        # 主 LLM 密钥（必填）
AMAP_MAPS_API_KEY=...                     # MCP 高德地图 Key（可选）
TAVILY_API_KEY=...                        # MCP Tavily 搜索 Key（可选）
IMAGE_TAG=latest                          # 拉取的镜像 tag（生产用）
```



### 3.2 启动方式

生产镜像方式：

```bash
docker compose -f docker-compose.prod.yml up -d
```

本地构建方式：

```bash
docker compose up -d --build
```

启动后常用访问地址（默认）：

- 前端：`http://localhost`
- 后端：`http://localhost:8989`
- MinIO 控制台：`http://localhost:9001`
- PostgreSQL：`http://localhost:5432`

---

## 四、ReAct Agent 引擎

入口：`POST /api/v1/ai/react-agent`，由 `AgentServiceImpl` 实现，返回 SSE 流。

### 4.1 主循环

核心循环 `runPlanningLoop()`，最大轮数 `RagConstant.MAX_ROUNDS = 10`。每轮做三件事：

1. **Plan**：调 LLM 输出 JSON 决策，`next_action` ∈ `tool` / `final_answer` / `clarify`；
2. **Act**：若决策为 `tool`，执行工具并做去重与失败计数；
3. **Observe**：把工具结果以 `observation` 追加进上下文，进入下一轮。

终止条件：到达最大轮数 / 模型给出 `final_answer` / 连续工具失败 / 触发任一层超时。

### 4.2 工具体系

内置 5 个工具：`file_read`、`file_write`、`file_search`、`grep_search`、`terminal_exec`；MCP 工具通过 `ToolServiceImpl` 一并合并进工具池，对 LLM 一视同仁。

### 4.3 安全与超时

- **安全沙箱**：工作区根目录强制校验、shell 命令白名单、写入扩展名黑名单；
- **四层超时**：Agent 全局 / 单轮预算 / LLM 决策 / 工具执行，全部由 `AgentToolProperties` 集中配置；
- **LLM 兜底**：主模型失败统一回退 Ollama `qwen3:8b`。

---

## 五、RAG 检索增强

本项目 RAG 分为两条主链路：

- **知识库入库链路**：将文档切分并写入 pgvector
- **在线问答检索链路**：对用户问题检索相关片段，拼接到提示词中再请求大模型

### 5.1 知识库入库流程

入口接口：`KnowledgeController#upload()`（仅管理员）

处理流程：

1. **文档解析**：使用 `TikaDocumentReader` 从 `MultipartFile` 抽取文本（支持 doc/docx/pdf/纯文本）；
2. **文档切分（Chunking）**：
   - 若同时含 `---` 与 `## Q:`，使用 `QaDocumentSplitter` 按 QA 对切分（保证 QA 完整性，提升回答准确率）；
   - 否则使用 `TokenTextSplitter` 按 token 规模切分；
3. **向量化存储**：调用 `vectorStore.add(splitDocuments)` 写入 pgvector；
4. **原始文件存储**：通过 `StorageUtil` 上传到 MinIO 或 阿里云 OSS（由 `STORAGE_TYPE` 决定）；
5. **元数据落库**：写入 `ali_oss_file`，包含 `file_name`、`url`、`vector_id`（分片 Document 的 id 列表）。

**失败处理**：若向量化失败，会抛出运行时异常并阻断后续步骤（避免「文件上传成功但向量缺失」的不一致状态）。

### 5.2 向量库与索引

向量存储基于 PostgreSQL + pgvector 实现：

1. **索引类型**：HNSW，兼顾检索效率与召回精度；
2. **距离度量**：余弦距离（COSINE_DISTANCE），适配文本语义匹配；
3. **向量维度**：1024 维，与 `gte-large-zh` 嵌入模型对齐；
4. **召回参数**：`TOP_K = 10`、`SIMILARITY_THRESHOLD = 0.71`，召回后 `filterByTermConsistency()` 做术语二次过滤。

### 5.3 提示词策略

提示词文件：

- `prompts/rag/rag-answer-system.md`
- `prompts/summary/summary-system.md`
- `prompts/react/react-plan-system.md`
- `prompts/react/react-plan-user.md`
- `prompts/react/react-answer-system.md`
- `prompts/react/react-skills-fragment.md`

关键点：

1. 普通 RAG 采用通用助手定位，优先级为「工具结果 > 知识库内容 > 通用知识」；
2. 命中知识库内容时，回答前缀标注「【根据知识库】：」；主要依赖通用知识时标注「【根据通用知识】：」；
3. ReAct 的规划与最终回答拆分为两套 prompt：规划阶段只输出 JSON 决策，最终回答阶段负责自然语言整合；
4. ReAct 的动态上下文通过独立 user template 注入，系统规则尽量收敛到 system prompt；
5. 所有运行时 prompt 通过 `PromptRegistry` 启动时预加载并缓存，避免重复读文件。

---

## 六、Skills 专业技能模块（待完善）

### 6.1 SKILL.md 格式

每个 Skill 是一个目录 `skills/<skill-name>/SKILL.md`：YAML frontmatter（`---` 包围，含 `name` / `description` / `trigger_keywords` 等）+ Markdown 正文。

### 6.2 加载与命中

- 启动时（`@PostConstruct`）按 **classpath → 文件系统** 顺序加载，同名 Skill 文件系统覆盖优先，便于热更新；
- 用户提问到达后，按 `trigger_keywords` 命中数量计分排序，注入分数最高的前 `MAX_SKILLS_PER_REQUEST = 3` 个 Skill 到系统提示词；
- 查询接口：`GET /api/v1/skills`。

---

## 七、MCP 工具集成

### 7.1 配置文件

`mcp-tools.json` 声明 MCP 服务，支持 `${VAR}` / `${VAR:default}` 占位符。加载逻辑：

1. **优先从外部路径**读取（运行目录 `mcp-tools.json`），便于运维替换，缺失则回退 classpath；
2. `resolveEnvPlaceholders()` 从 `System.getenv()` 注入占位符；
3. **若必需变量缺失，自动禁用对应 MCP 服务** 而非抛异常 —— 用户未配置 MCP 时无感。

### 7.2 三种传输模式

- **stdio**：通过 `ProcessBuilder` 启动子进程，劫持 stdin / stdout 做 JSON-RPC 通信（配置含 `command` 字段时启用）；
- **http**：直连 HTTP JSON-RPC 端点；
- **sse**：通过 SSE 流接收 endpoint 事件后再发起 JSON-RPC（URL 含 `/sse` 时启用）。

### 7.3 接口

- `GET /api/v1/mcp/list`：MCP 服务及工具列表；
- `POST /api/v1/mcp/reload`：热重载配置。

---

## 八、会话记忆管理

本项目的会话记忆采用**数据库持久化 + 滑动窗口**方案，可保障：保留最近 10 条历史对话、每次对话均做持久化、消息类型分为 user/assistant/system 三类、支持跨请求 / 跨刷新恢复上下文、用户与会话隔离。

### 8.1 滑动窗口

入口：`AskServiceImpl / AgentServiceImpl` → `ChatMessageServiceImpl#getRecentMessages()`。

处理流程：先将用户消息保存至 `chat_message` 表，再查询最近 `MEMORY_SIZE` 条作为对话上下文，最后转换为 Spring AI `Message` 列表（user / assistant 成对组织）。

`sanitizeForContext()` 会移除 `<!-- thinking_process_start --> ... <!-- thinking_process_end -->` 之间的「思考过程」标记，防止链式污染。

**关键常量**：`RagConstant.MEMORY_SIZE`（当前为 10）。

### 8.2 滚动摘要

`SummaryServiceImpl` 在每完成 10 条消息后触发一次（`totalMessages % MEMORY_SIZE <= 1`），结果写回 `chat_session.summary`，下一轮提示词中以背景信息出现。

---

## 九、本地大模型集成

本项目通过 Ollama 本地部署 **qwen3:8b** 对话模型与 **gte-large-zh** 嵌入模型。

基于 **Spring AI** 集成，要点如下：

1. 依赖：`spring-ai-ollama-spring-boot-starter`、`spring-ai-openai-spring-boot-starter`；
2. 配置：`application.yml` → `spring.ai.ollama.*` 与 `spring.ai.openai.*`；
3. **双模型路由**：`LLMProviderServiceImpl` 按模型名分发到 `openAiChatModel` 或 `ollamaChatModel`；
4. **统一兜底**：任何主调用失败时回退到本地 Ollama `qwen3:8b`，保障可用性；
5. 嵌入模型输出维度必须与 pgvector 数据表保持一致（当前为 1024 维）。

---

## 十、对象存储抽象

`StorageUtil` 定义统一存储 API：`upload / delete / download / getObject / getPresignedUrl`。

通过 `storage.type` 切换实现：

- `minio`：`MinioConfig` 注入 MinIO 实现；
- `alioss`：`OssConfiguration` 注入阿里云 OSS 实现。

业务层注入 `StorageUtil` 抽象类型，无需感知具体实现。文件元数据统一落到 `ali_oss_file` 表（保留历史命名）。

---

## 十一、鉴权设计

- 拦截器：`JwtTokenUserInterceptor` 拦截 `/api/v1/**`，排除登录注册；`AdminInterceptor` 仅拦截敏感接口（如 `knowledge/file/upload`、`knowledge/delete`），校验 `role == 1`；
- 双令牌：用户与管理员独立的 secret-key、token-name、ttl，配置在 `cs.jwt.*`；
- 工具：`JwtUtil`（HS256 签名）；
- 密码：`UserServiceImpl` 使用 `DigestUtils.md5DigestAsHex` 加密；
- `BaseContext` 在拦截器中存放当前 `userId`，供下游 Service 使用。

---

## 十二、关键接口速查

- RAG 对话（SSE）：`POST /api/v1/ai/rag`
- ReAct Agent（SSE）：`POST /api/v1/ai/react-agent`
- 查询会话历史：`POST /api/v1/ai/rag/history`
- 查询会话列表：`POST /api/v1/ai/rag/sessions`
- 删除会话（逻辑删除）：`POST /api/v1/ai/rag/sessions/delete`

- 上传知识库文件（管理员）：`POST /api/v1/knowledge/file/upload`（multipart）
- 查询文件：`GET /api/v1/knowledge/contents`
- 删除文件（管理员）：`DELETE /api/v1/knowledge/delete`
- 下载文件（批量）：`GET /api/v1/knowledge/download`
- 下载文件（流）：`GET /api/v1/knowledge/downloadFile/{id}`

- Skills 列表：`GET /api/v1/skills`
- MCP 服务及工具列表：`GET /api/v1/mcp/list`
- 热重载 MCP：`POST /api/v1/mcp/reload`
- 用户反馈：`POST /api/v1/feedback/submit`

---

## 十三、未来优化方向

### 13.1 工具集成

- 集成更多本地工具与 MCP 工具，提升 Agent 能力和回答准确性；
- Agent 行为可配置化：用户自定义工具白名单、超时与最大轮数。

### 13.2 会话记忆升级

- 当前为滑动窗口「短记忆」，可引入：
  - 对话摘要记忆（定期总结历史上下文，减少 token 消耗）
  - 对话语义检索（将历史消息向量化后按相似度检索相关历史）

### 13.3 多模型 / 容灾策略

- 模型选择策略：根据问题类型路由（命中知识库走本地小模型，未命中走外部大模型）；
- 超时 / 重试 / 熔断：对流式输出增加超时与错误兜底响应。

### 13.4 知识库运营与质量控制

- 增量更新：同文件多次上传时支持覆盖 / 版本管理，并同步清理旧向量；
- 评价闭环：收集用户反馈（好 / 差、纠错），反向驱动知识库更新。
