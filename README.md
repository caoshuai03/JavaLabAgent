# JavaLab Agent v2.0

<p align="center">
  <img src="javalab-agent-front/src/assets/logo.png" alt="Logo" width="200" height="200">
</p>

基于 **Spring AI** 构建的智能助手系统，集成 **RAG**（检索增强生成）、**ReAct Agent** 引擎与 **Skills** 专业技能模块，支持本地大模型与 **MCP** 工具生态。

![img.png](javalab-agent-back/src/main/resources/img.png)

## 🌟 主要功能

- 🤖 **ReAct Agent 引擎**：基于 Plan / Act / Observe 多轮循环的任务规划与执行，支持工具调用、去重、超时熔断与失败兜底。
- 🛠️ **MCP 工具集成**：兼容 Model Context Protocol，支持 `stdio` / `http` / `sse` 三种传输模式，一份 `mcp-tools.json` 即可热接入外部工具。
- 📚 **RAG 知识库**：自动识别 QA 文档与普通文档分别采用 `QaDocumentSplitter` / `TokenTextSplitter`，使用 PostgreSQL + pgvector（HNSW + 余弦距离）做向量检索。
- 🎯 **Skills 专业技能**：通过 `SKILL.md` (YAML frontmatter + 正文) 描述技能，注入给大模型 skill。
- 🧠 **会话记忆与摘要**：滑动窗口+ 滚动摘要，支持跨刷新恢复上下文。
- 💬 **流式交互**：基于 SSE 的事件流，前端可实时渲染思考链路。
- 🖥️ **现代化前端**：Vue 3 + Element Plus 响应式 UI，包含会话、知识库、MCP 设置、Skills 管理等页面。
- 🔐 **完善鉴权**：基于 JWT 的双令牌（用户 / 管理员），管理员令牌专用于知识库写操作。

## 🛠️ 技术栈

| 技术 | 说明 |
| :--- | :--- |
| **Spring Boot 3.4** + **Java 17** | 核心开发框架 |
| **Spring AI** | LLM / Embedding / VectorStore 抽象与编排 |
| **PostgreSQL + pgvector** | 关系型数据 + 1024 维向量存储（HNSW 索引） |
| **Ollama** | 本地大模型 |
| **MinIO / 阿里云 OSS** | 对象存储二选一 |
| **Vue 3 + Element Plus** | 现代化前端 |

## 🚀 快速开始

### 1. 环境配置

在项目根目录创建 `.env` 文件，按需填写：

```env
# Ollama 服务地址（必填）
OLLAMA_BASE_URL=http://xxx:11434

# =================【API Key（敏感信息）】=================
# 百度千帆 / OpenAI 兼容接口的 API Key（必填）
OPENAI_API_KEY=your_openai_api_key
# MCP 工具：高德地图 API Key（可选，不配置则自动禁用该 MCP 服务）
AMAP_MAPS_API_KEY=your_amap_maps_api_key
# MCP 工具：Tavily 搜索 API Key（可选，不配置则自动禁用该 MCP 服务）
TAVILY_API_KEY=your_tavily_api_key

# =================【存储配置（二选一）】=================
# 存储类型：minio（默认）或 alioss（阿里云）
# STORAGE_TYPE=minio

# ---------- 方式一：MinIO 配置（推荐，无需云服务） ----------
# MinIO 会随 docker compose 自动启动，以下为默认值，可不修改
# MINIO_ROOT_USER=minioadmin
# MINIO_ROOT_PASSWORD=minioadmin123
# MINIO_BUCKET=javalab

# ---------- 方式二：阿里云 OSS 配置（可选配置） ----------
# OSS_ACCESS_KEY_ID=your_access_key_id
# OSS_ACCESS_KEY_SECRET=your_access_key_secret
# OSS_BUCKET_NAME=your_bucket_name
# OSS_ENDPOINT=your_oss_endpoint

# =================【可选配置】=================
# PostgreSQL 数据库配置（有默认值，可不设置）
# POSTGRES_USER=postgres
# POSTGRES_PASSWORD=admin
# POSTGRES_DB=postgres
```

### 2. Docker 一键启动

确保已安装 Docker 和 Docker Compose，然后在根目录执行。

#### 生产环境启动

```bash
docker compose -f docker-compose.prod.yml up -d
```

#### 开发 / 测试环境启动

```bash
docker compose up -d --build
```

#### 更新单个容器

```bash
docker compose up -d --build --no-deps frontend
```

访问地址：

- **前端地址**：<http://localhost>
- **后端地址**：<http://localhost:8989>
- **MinIO 控制台**：<http://localhost:9001>（默认用户名 `minioadmin`，密码 `minioadmin123`）
- **数据库**：<http://localhost:5432>（PostgreSQL + pgvector）

### 3. 辅助命令

1、清理所有容器与数据卷（谨慎使用，会删除所有数据）

```bash
docker compose down -v
```

2、镜像构建与推送（用于部署分发）

```bash
# 构建并推送后端镜像
docker build -t mailacs/javalabagent-backend:latest -f javalab-agent-back/Dockerfile javalab-agent-back
docker push mailacs/javalabagent-backend:latest

# 构建并推送前端镜像
docker build -t mailacs/javalabagent-frontend:latest -f javalab-agent-front/Dockerfile javalab-agent-front
docker push mailacs/javalabagent-frontend:latest
```

## 📂 仓库结构

```
JavaLabAgent/
├── javalab-agent-back/        # Spring Boot 后端
│   ├── src/main/java/com/cs/rag/
│   │   ├── controller/         # REST 接口（Ai / Knowledge / Mcp / Skill / User / Feedback）
│   │   ├── service/            # 业务逻辑（Agent / Ask / Knowledge / Mcp / Skill / Summary ...）
│   │   ├── config/             # 拦截器、JWT、存储、Agent 工具配置
│   │   └── ...
│   └── src/main/resources/
│       ├── application.yml     # 主配置
│       ├── mcp-tools.json      # MCP 服务声明（支持 ${ENV} 占位符）
│       ├── prompts/            # RAG / ReAct / Summary 提示词模板
│       └── skills/             # 内置 Skills（SKILL.md）
├── javalab-agent-front/        # Vue 3 前端
├── sql/init.sql                # PostgreSQL 初始化脚本（含 pgvector）
├── docker-compose.yml          # 本地构建编排
├── docker-compose.prod.yml     # 生产镜像编排
└── TECHNICAL_DOC.md            # 技术文档（架构、接口、设计细节）
```

## 📚 文档

- **[技术文档](TECHNICAL_DOC.md)** — 系统架构、模块介绍、技术方案、未来优化。

---

## 📅 未来规划

- [ ] **plan-execute 模块**：在 ReAct 之外引入“先产出全局计划、再分阶段执行”的模式，适合长链路、多依赖、多工具协作任务；最终实现多个 agent 框架的协作模式。
- [ ] **长期记忆 + 用户画像**：沉淀跨会话稳定信息、用户偏好与历史问题模式，详见 [4.3](#43-长期记忆的未来优化设计)；
- [ ] **自迭代 Agent 经验层**：引入 `agent.md` 一类可持续演进的经验文档，沉淀高频问题的处理套路、常见故障排查路径与课程领域最佳实践；
- [ ] **subagent / 多 Agent 协作**：把检索、执行、验证、总结等能力拆给不同角色的 Agent，支持串行分工与并行协作，提升复杂任务处理上限；
- [ ] **通用 Agent 平台化**：从“Java 实验教学助手”逐步抽象为通用 Agent 底座，把知识库、工具、Memory、Skill、MCP、评测等能力做成可复用模块，便于扩展到更多场景和其他业务领域；
- [ ] **Harness Engineering 完善**：从 Prompt 之外，系统化补齐 Agent 外围基础设施，包括上下文管理、记忆写入门控、工具治理、状态持久化、错误恢复、观测埋点与安全护栏，让能力提升不只依赖模型本身；
- [ ] **自进化 Agent**：参考自进化 Hermes Agent 的思路，把“做任务 → 验证结果 → 沉淀经验 → 复用经验”做成闭环，不只沉淀 `agent.md`，也让 Skill 能随使用过程持续修正和迭代；
- [ ] **自迭代 Skills**：把一次次人工纠偏、成功案例、标准流程沉淀为可复用 Skill，并支持增量更新、版本对比、回滚和人工审核，避免错误经验被无限放大；
- [ ] **框架路线升级评估**：当前系统基于 Spring AI，后续可系统评估 Spring AI、LangGraph、LangChain 等框架在状态管理、长链路编排、持久化恢复、人机协同、生态扩展上的优缺点；若 Python 生态在 Agent 编排层更成熟，也可逐步演进到 `LangGraph + Python` 这类更利于复杂工作流扩展的方案；
- [ ] **多模态能力**：扩展图片识别、语音输入、语音回复等能力，让实验教学场景从文本交互走向更自然的人机协作；
- [ ] **观测与评测体系**：补齐对问答质量、召回效果、工具调用成功率、超时分布与用户反馈的评测闭环，让后续优化有数据依据。
