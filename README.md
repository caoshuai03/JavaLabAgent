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
- **后端接口**：<http://localhost:8989>
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

- **[技术文档](TECHNICAL_DOC.md)** — 系统架构、模块设计、关键流程与接口速查

---

## 📅 未来规划

- [x] 自动化技能 (Skills) 模块
- [ ] plan-execute 模块
- [ ] 长期记忆，与用户画像记忆
- [ ] 多模态能力增强（图片、语音识别）
- [ ] 更多 MCP 官方工具集成
- [ ] Skills 的在线编辑和版本管理
- [ ] 知识库增量更新与版本管理
