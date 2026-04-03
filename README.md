# JavaLab Agent v2.0

<p align="center">
  <img src="javalab-agent-front/src/assets/logo.png" alt="Logo" width="200" height="200">
</p>

基于 **Spring AI** 构建的智能助手系统，集成了 **RAG**（检索增强生成）与 **ReAct Agent** 框架，支持本地大模型及 **MCP** 工具生态。

![img.png](javalab-agent-back/src/main/resources/img.png)

## 📢 时间线

[2026.04.03] 🔥 发布 JavaLab Agent v2.0。加入 Skills 专业技能模块，支持专业知识领域的灵活扩展与自动感知，实现上下文智能注入。前端架构全面升级，基于 Vue 3 + Element Plus 构建现代化响应式交互界面。加入增强 ReAct Agent 引擎，实现 Skills 的自动化匹配与动态注入能力。

[2026.01.22] 发布 JavaLab Agent v1.0。上线 RAG 知识库管理系统，支持私有知识的智能检索与问答。接入 MCP 工具生态，扩展 Agent 能力边界。支持 SSE 流式实时交互，提供流畅的对话体验。

[2025.11.15] 发布 JavaLab Agent v0.3。引入上下文管理引擎，支持多轮对话历史追踪与智能摘要生成。优化知识库检索算法，显著提升搜索精准度与响应性能。完善用户权限管理体系。

[2025.10.08] 发布 JavaLab Agent v0.2。实现基础对话交互能力，支持本地大模型私有化部署。

[2025.09.01] JavaLab Agent 项目立项。

## 🌟 主要功能

- 🤖 **智能智能体 (ReAct Agent)**：基于 ReAct 模式的任务规划与执行，支持多步思考与工具调用。
- 🛠️ **MCP 工具集成**：兼容 Model Context Protocol，可动态扩展外部工具与服务。
- 📚 **RAG 知识库管理**：支持文档上传、自动切片、向量存储及精准检索问答。
- 🎯 **Skills 专业技能**：可扩展的专业技能模块，支持自动触发和上下文注入。
- 💬 **流式交互体验**：基于 SSE 的流式响应，支持对话上下文记忆与摘要。
- 🖥️️ **现代化前端界面**：Vue 3 + Element Plus 构建的响应式 UI，新增 Skills 管理页面。
- 🔐 **完善安全体系**：基于 JWT 的身份认证与精细化权限管理。

## 🛠️ 技术栈

| 技术 | 说明 |
| :--- | :--- |
| **Spring Boot 3.4** | 核心开发框架 |
| **Spring AI** | AI 模型集成与编排 |
| **PostgreSQL + pgvector** | 向量及关系型数据存储 |
| **MinIO / AliOSS** | 对象存储支持 |
| **Vue.js 3 + Element Plus** | 现代化前端界面框架 |
| **Vite** | 前端构建工具 |

## 🚀 快速开始

### 1. 环境配置

在项目根目录创建 .env 文件，填写以下配置项（按需选择对应配置）：

```env
# Ollama 服务地址
OLlama_BASE_URL=http://xxx:11434
# 镜像tag 镜像地址：mailacs/javalabagent-backend
IMAGE_TAG=2026012201

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
# PostgreSQL 数据库配置 (有默认值，可不设置)
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

访问地址：

- **前端地址**: <http://localhost>
- **后端接口**: <http://localhost:8989>
- **MinIO 控制台**: <http://localhost:9001>（用户名：minioadmin，密码：minioadmin123）
- **数据库**: <http://localhost:5432> (PostgreSQL + pgvector)

### 辅助命令

1、清理所有容器与数据卷（谨慎使用，会删除所有数据）

```bash
docker compose down -v
```

2、镜像推送（用于部署分发）

```bash
# 构建并推送后端镜像
docker build -t mailacs/javalabagent-backend:latest -f javalab-agent-back/Dockerfile javalab-agent-back
docker push mailacs/javalabagent-backend:latest
 
# 构建并推送前端镜像
docker build -t mailacs/javalabagent-frontend:latest -f javalab-agent-front/Dockerfile javalab-agent-front
docker push mailacs/javalabagent-frontend:latest
```

## 📚 文档

- **[技术文档](TECHNICAL_DOC.md)** - 深入了解系统架构和实现细节

---

## 📅 未来规划

- [x] 自动化技能 (Skills) 模块
- [ ] 多模态能力增强（图片、语音识别）
- [ ] 更多 MCP 官方工具集成
- [ ] Skills 的在线编辑和版本管理
